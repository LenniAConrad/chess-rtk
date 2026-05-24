package application.gui.workbench.ui;

import chess.book.render.NotationPieceSvg;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import utility.Svg;
import utility.Svg.DocumentModel;

/**
 * Shared inline chess-notation painter for Swing surfaces.
 *
 * <p>The painter replaces SAN piece letters, figurine placeholders, and
 * coordinate-style NN labels such as {@code Kc5 / qd1} with the neutral
 * outline SVG pieces used by the book report. Files, ranks, captures, scores,
 * and ordinary prose remain text.</p>
 */
public final class NotationPainter {

    /**
     * Marker for text-only segments.
     */
    private static final char NO_PIECE = '\0';

    /**
     * White king notation placeholder.
     */
    private static final char KING_PLACEHOLDER = '\u2654';

    /**
     * White queen notation placeholder.
     */
    private static final char QUEEN_PLACEHOLDER = '\u2655';

    /**
     * White rook notation placeholder.
     */
    private static final char ROOK_PLACEHOLDER = '\u2656';

    /**
     * White bishop notation placeholder.
     */
    private static final char BISHOP_PLACEHOLDER = '\u2657';

    /**
     * White knight notation placeholder.
     */
    private static final char KNIGHT_PLACEHOLDER = '\u2658';

    /**
     * White pawn notation placeholder.
     */
    private static final char PAWN_PLACEHOLDER = '\u2659';

    /**
     * Gap between a piece SVG and adjacent text.
     */
    private static final int PIECE_GAP = 2;

    /**
     * Ellipsis used when an inline notation label is too wide.
     */
    private static final String ELLIPSIS = "...";

    /**
     * Parsed cutout king SVG.
     */
    private static final DocumentModel KING_DOCUMENT = notationDocument(KING_PLACEHOLDER);

    /**
     * Parsed cutout queen SVG.
     */
    private static final DocumentModel QUEEN_DOCUMENT = notationDocument(QUEEN_PLACEHOLDER);

    /**
     * Parsed cutout rook SVG.
     */
    private static final DocumentModel ROOK_DOCUMENT = notationDocument(ROOK_PLACEHOLDER);

    /**
     * Parsed cutout bishop SVG.
     */
    private static final DocumentModel BISHOP_DOCUMENT = notationDocument(BISHOP_PLACEHOLDER);

    /**
     * Parsed cutout knight SVG.
     */
    private static final DocumentModel KNIGHT_DOCUMENT = notationDocument(KNIGHT_PLACEHOLDER);

    /**
     * Parsed cutout pawn SVG.
     */
    private static final DocumentModel PAWN_DOCUMENT = notationDocument(PAWN_PLACEHOLDER);

    /**
     * Utility class; prevents instantiation.
     */
    private NotationPainter() {
        // utility
    }

    /**
     * Draws inline chess notation, eliding to fit the available width.
     *
     * @param g graphics context
     * @param text notation text
     * @param x left x coordinate
     * @param baseline text baseline
     * @param maxWidth maximum paint width
     * @param color foreground color
     */
    public static void draw(Graphics2D g, String text, int x, int baseline, int maxWidth, Color color) {
        FontMetrics metrics = g.getFontMetrics();
        draw(g, text, x, baseline, maxWidth, color, iconSize(metrics));
    }

    /**
     * Draws inline chess notation with an explicit SVG icon size.
     *
     * @param g graphics context
     * @param text notation text
     * @param x left x coordinate
     * @param baseline text baseline
     * @param maxWidth maximum paint width
     * @param color foreground color
     * @param iconSize piece icon size in pixels
     */
    public static void draw(Graphics2D g, String text, int x, int baseline,
            int maxWidth, Color color, int iconSize) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return;
        }
        String fitted = elideNotation(text, g.getFontMetrics(), iconSize, maxWidth);
        int cursor = x;
        int iconY = iconY(g.getFontMetrics(), baseline, iconSize);
        for (Segment segment : parseSegments(fitted)) {
            if (segment.isPiece()) {
                Svg.draw(documentFor(segment.piece()), g, cursor, iconY, iconSize, iconSize, color);
                cursor += iconSize + PIECE_GAP;
            } else if (!segment.text().isEmpty()) {
                g.setColor(color);
                g.drawString(segment.text(), cursor, baseline);
                cursor += g.getFontMetrics().stringWidth(segment.text());
            }
        }
    }

    /**
     * Measures notation text with SVG piece advances.
     *
     * @param text notation text
     * @param metrics font metrics
     * @param iconSize piece icon size in pixels
     * @return width in pixels
     */
    public static int width(String text, FontMetrics metrics, int iconSize) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (Segment segment : parseSegments(text)) {
            width += segment.isPiece()
                    ? iconSize + PIECE_GAP
                    : metrics.stringWidth(segment.text());
        }
        return width;
    }

    /**
     * Counts piece SVG segments that would be painted.
     *
     * @param text notation text
     * @return piece segment count
     */
    public static int pieceSvgCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Segment segment : parseSegments(text)) {
            if (segment.isPiece()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns a font-relative piece icon size.
     *
     * @param metrics active font metrics
     * @return icon size in pixels
     */
    public static int iconSize(FontMetrics metrics) {
        return Math.max(10, metrics.getHeight() + 3);
    }

    /**
     * Parses text into plain text and piece SVG segments.
     *
     * @param text notation text
     * @return parsed segments
     */
    private static List<Segment> parseSegments(String text) {
        List<Segment> parsed = new ArrayList<>();
        StringBuilder pendingText = new StringBuilder();
        boolean tokenStart = true;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                pendingText.append(ch);
                tokenStart = true;
                continue;
            }
            char placeholder = placeholderFor(ch);
            if (placeholder != NO_PIECE) {
                flushText(parsed, pendingText);
                parsed.add(Segment.piece(placeholder));
                tokenStart = false;
                continue;
            }
            if (ch == '=' && i + 1 < text.length()) {
                char promoted = pieceForLetter(text.charAt(i + 1), true);
                if (promoted != NO_PIECE) {
                    pendingText.append(ch);
                    flushText(parsed, pendingText);
                    parsed.add(Segment.piece(promoted));
                    i++;
                    tokenStart = false;
                    continue;
                }
            }
            char coordinatePiece = coordinatePieceAt(text, i);
            if (coordinatePiece != NO_PIECE) {
                flushText(parsed, pendingText);
                parsed.add(Segment.piece(coordinatePiece));
                tokenStart = false;
                continue;
            }
            char sanPiece = tokenStart ? sanPieceAt(text, i) : NO_PIECE;
            if (sanPiece != NO_PIECE) {
                flushText(parsed, pendingText);
                parsed.add(Segment.piece(sanPiece));
                tokenStart = false;
                continue;
            }
            pendingText.append(ch);
            if (!(Character.isDigit(ch) || ch == '.' || ch == '(' || ch == '[' || ch == '{')) {
                tokenStart = false;
            }
        }
        flushText(parsed, pendingText);
        return parsed;
    }

    /**
     * Appends pending text to the segment list.
     *
     * @param segments parsed segments
     * @param pendingText pending text buffer
     */
    private static void flushText(List<Segment> segments, StringBuilder pendingText) {
        if (!pendingText.isEmpty()) {
            segments.add(Segment.text(pendingText.toString()));
            pendingText.setLength(0);
        }
    }

    /**
     * Returns a placeholder for an existing figurine character.
     *
     * @param ch character
     * @return placeholder, or {@link #NO_PIECE}
     */
    private static char placeholderFor(char ch) {
        return NotationPieceSvg.isPlaceholder(ch) ? ch : NO_PIECE;
    }

    /**
     * Returns the piece placeholder for a coordinate-style piece label.
     *
     * @param text full text
     * @param index current character index
     * @return piece placeholder, or {@link #NO_PIECE}
     */
    private static char coordinatePieceAt(String text, int index) {
        if (index + 2 >= text.length()) {
            return NO_PIECE;
        }
        if (index > 0 && Character.isLetterOrDigit(text.charAt(index - 1))) {
            return NO_PIECE;
        }
        char piece = pieceForLetter(text.charAt(index), true);
        if (piece == NO_PIECE) {
            return NO_PIECE;
        }
        char file = text.charAt(index + 1);
        char rank = text.charAt(index + 2);
        return file >= 'a' && file <= 'h' && rank >= '1' && rank <= '8'
                ? piece
                : NO_PIECE;
    }

    /**
     * Returns the piece placeholder for a SAN-style move token.
     *
     * @param text full text
     * @param index current character index
     * @return piece placeholder, or {@link #NO_PIECE}
     */
    private static char sanPieceAt(String text, int index) {
        if (index + 1 >= text.length()) {
            return NO_PIECE;
        }
        char piece = text.charAt(index);
        if (!Character.isUpperCase(piece)) {
            return NO_PIECE;
        }
        char placeholder = pieceForLetter(piece, false);
        if (placeholder == NO_PIECE) {
            return NO_PIECE;
        }
        return looksLikeSanMoveRest(text, index + 1)
                ? placeholder
                : NO_PIECE;
    }

    /**
     * Returns whether the text after an uppercase piece letter looks like a SAN
     * move target rather than ordinary prose.
     *
     * @param text full text
     * @param index first character after the piece letter
     * @return true when the rest resembles SAN
     */
    private static boolean looksLikeSanMoveRest(String text, int index) {
        int end = index;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        for (int i = index; i < end; i++) {
            if (text.charAt(i) >= 'a' && text.charAt(i) <= 'h'
                    && i + 1 < end && text.charAt(i + 1) >= '1' && text.charAt(i + 1) <= '8') {
                return true;
            }
        }
        return index < end && text.charAt(index) == 'x';
    }

    /**
     * Converts a notation piece letter to a neutral placeholder.
     *
     * @param piece piece letter
     * @param allowPawn true to allow pawn markers
     * @return placeholder, or {@link #NO_PIECE}
     */
    private static char pieceForLetter(char piece, boolean allowPawn) {
        return switch (piece) {
            case 'K', 'k' -> KING_PLACEHOLDER;
            case 'Q', 'q' -> QUEEN_PLACEHOLDER;
            case 'R', 'r' -> ROOK_PLACEHOLDER;
            case 'B', 'b' -> BISHOP_PLACEHOLDER;
            case 'N', 'n' -> KNIGHT_PLACEHOLDER;
            case 'P', 'p' -> allowPawn ? PAWN_PLACEHOLDER : NO_PIECE;
            default -> NO_PIECE;
        };
    }

    /**
     * Elides notation without splitting a piece segment.
     *
     * @param text notation text
     * @param metrics font metrics
     * @param iconSize piece icon size
     * @param maxWidth maximum width
     * @return fitted text
     */
    private static String elideNotation(String text, FontMetrics metrics, int iconSize, int maxWidth) {
        if (width(text, metrics, iconSize) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = metrics.stringWidth(ELLIPSIS);
        int limit = Math.max(0, maxWidth - ellipsisWidth);
        String trimmed = text;
        while (!trimmed.isEmpty() && width(trimmed, metrics, iconSize) > limit) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? ELLIPSIS : trimmed.stripTrailing() + ELLIPSIS;
    }

    /**
     * Computes the top y coordinate for a baseline-aligned piece icon.
     *
     * @param metrics font metrics
     * @param baseline text baseline
     * @param iconSize icon size
     * @return icon y coordinate
     */
    private static int iconY(FontMetrics metrics, int baseline, int iconSize) {
        return baseline - metrics.getAscent() + Math.max(0, (metrics.getHeight() - iconSize) / 2);
    }

    /**
     * Parses the report-style cutout SVG for one placeholder.
     *
     * @param placeholder notation placeholder
     * @return parsed SVG document
     */
    private static DocumentModel notationDocument(char placeholder) {
        return Svg.parse(NotationPieceSvg.svg(placeholder));
    }

    /**
     * Returns the parsed cutout SVG for one placeholder.
     *
     * @param piece notation placeholder
     * @return parsed SVG document
     */
    private static DocumentModel documentFor(char piece) {
        return switch (piece) {
            case KING_PLACEHOLDER -> KING_DOCUMENT;
            case QUEEN_PLACEHOLDER -> QUEEN_DOCUMENT;
            case ROOK_PLACEHOLDER -> ROOK_DOCUMENT;
            case BISHOP_PLACEHOLDER -> BISHOP_DOCUMENT;
            case KNIGHT_PLACEHOLDER -> KNIGHT_DOCUMENT;
            case PAWN_PLACEHOLDER -> PAWN_DOCUMENT;
            default -> throw new IllegalArgumentException("Unknown notation piece: " + piece);
        };
    }

    /**
     * Parsed notation rendering segment.
     *
     * @param text text content for text segments
     * @param piece neutral cutout-piece placeholder for SVG segments
     */
    private record Segment(String text, char piece) {

        /**
         * Creates a text segment.
         *
         * @param value segment text
         * @return text segment
         */
        static Segment text(String value) {
            return new Segment(value, NO_PIECE);
        }

        /**
         * Creates a piece segment.
         *
         * @param value notation placeholder
         * @return piece segment
         */
        static Segment piece(char value) {
            return new Segment("", value);
        }

        /**
         * Returns whether this segment paints a piece SVG.
         *
         * @return true for piece segments
         */
        boolean isPiece() {
            return piece != NO_PIECE;
        }
    }
}
