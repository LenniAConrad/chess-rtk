package application.gui.workbench;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * Shared low-level rendering primitives used by the Workbench NN visualizer
 * panels.
 *
 * <p>Stateless static helpers: heatmaps, bar lists, mini chess boards, wired
 * connections, section headers, mode-toggle buttons, and statistic helpers.
 * Mirrors the C++ visualizer's TensorViz module so view classes stay terse.</p>
 */
final class WorkbenchTensorViz {

    /**
     * Positive-activation accent (used for "up" / gain).
     */
    static final Color POSITIVE = new Color(38, 130, 75);

    /**
     * Negative-activation accent (used for "down" / loss).
     */
    static final Color NEGATIVE = new Color(178, 53, 53);

    /**
     * Trunk / data-flow accent.
     */
    static final Color TRUNK = new Color(196, 121, 47);

    /**
     * Policy-branch accent.
     */
    static final Color POLICY = new Color(48, 102, 168);

    /**
     * Value-branch accent.
     */
    static final Color VALUE = new Color(150, 60, 142);

    /**
     * Neutral-fill background for cells with no signal.
     */
    static final Color NEUTRAL = new Color(232, 236, 240);

    /**
     * Lightest heatmap fill (signed scale, near zero).
     */
    static final Color HEAT_ZERO = new Color(240, 244, 247);

    /**
     * Prevents instantiation.
     */
    private WorkbenchTensorViz() {
        // utility
    }

    /**
     * Draws a labelled section header strip.
     *
     * @param g graphics
     * @param r section rectangle
     * @param title title text
     * @param subtitle subtitle text (may be null)
     */
    static void drawSectionHeader(Graphics2D g, Rectangle r, String title, String subtitle) {
        g.setColor(WorkbenchTheme.ELEVATED_SOLID);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(14, Font.BOLD));
        g.drawString(title, r.x + 10, r.y + 18);
        if (subtitle != null && !subtitle.isEmpty()) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            g.drawString(subtitle, r.x + 10, r.y + 34);
        }
    }

    /**
     * Draws a thin labelled card frame.
     *
     * @param g graphics
     * @param r card rectangle
     * @param title card title
     * @param subtitle subtitle (may be null)
     * @param accent accent color used for the left edge
     */
    static void drawCard(Graphics2D g, Rectangle r, String title, String subtitle, Color accent) {
        g.setColor(WorkbenchTheme.PANEL_SOLID);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        if (accent != null) {
            g.setColor(accent);
            g.fillRect(r.x, r.y, 3, r.height);
        }
        if (title != null) {
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(12, Font.BOLD));
            g.drawString(title, r.x + 8, r.y + 14);
        }
        if (subtitle != null) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
            g.drawString(subtitle, r.x + 8, r.y + 28);
        }
    }

    /**
     * Renders a 2D heatmap into a rectangle. Negative-symmetric scale.
     *
     * @param g graphics
     * @param r destination rectangle
     * @param data flat row-major values
     * @param cols column count
     * @param rows row count
     * @param scale absolute max for the color ramp (auto if &lt;= 0)
     * @param signed true for diverging blue/red ramp, false for sequential
     */
    static void drawHeatmap(Graphics2D g, Rectangle r, float[] data, int cols, int rows,
            float scale, boolean signed) {
        if (data == null || cols <= 0 || rows <= 0 || data.length < cols * rows) {
            drawEmpty(g, r);
            return;
        }
        float s = scale;
        if (s <= 0.0f) {
            for (int i = 0; i < cols * rows; ++i) {
                float a = Math.abs(data[i]);
                if (a > s) {
                    s = a;
                }
            }
            if (s <= 0.0f) {
                s = 1.0f;
            }
        }
        double cw = r.width / (double) cols;
        double ch = r.height / (double) rows;
        for (int row = 0; row < rows; ++row) {
            for (int col = 0; col < cols; ++col) {
                int cellX = (int) Math.floor(r.x + col * cw);
                int cellY = (int) Math.floor(r.y + row * ch);
                int cellW = (int) Math.ceil(cw + 1);
                int cellH = (int) Math.ceil(ch + 1);
                float v = data[row * cols + col] / s;
                v = clamp(v, -1.0f, 1.0f);
                g.setColor(signed ? signedRamp(v) : sequentialRamp(Math.max(0.0f, v)));
                g.fillRect(cellX, cellY, cellW, cellH);
            }
        }
        g.setColor(WorkbenchTheme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
    }

    /**
     * Draws an empty placeholder rectangle.
     *
     * @param g graphics
     * @param r destination rectangle
     */
    static void drawEmpty(Graphics2D g, Rectangle r) {
        g.setColor(NEUTRAL);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
    }

    /**
     * Returns a diverging blue/red color for a signed value in [-1, 1].
     *
     * @param value signed value
     * @return color
     */
    static Color signedRamp(float value) {
        float v = clamp(value, -1.0f, 1.0f);
        if (v >= 0.0f) {
            return lerp(HEAT_ZERO, NEGATIVE, v);
        }
        return lerp(HEAT_ZERO, POLICY, -v);
    }

    /**
     * Returns a sequential color for a value in [0, 1].
     *
     * @param value sequential value
     * @return color
     */
    static Color sequentialRamp(float value) {
        float v = clamp(value, 0.0f, 1.0f);
        return lerp(HEAT_ZERO, new Color(200, 90, 40), v);
    }

    /**
     * Linearly blends two colors.
     *
     * @param from start color
     * @param to end color
     * @param t blend in [0, 1]
     * @return blended color
     */
    static Color lerp(Color from, Color to, float t) {
        float c = clamp(t, 0.0f, 1.0f);
        int r = Math.round(from.getRed() + (to.getRed() - from.getRed()) * c);
        int g = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * c);
        int b = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * c);
        int a = Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * c);
        return new Color(r, g, b, a);
    }

    /**
     * Draws a row of labelled bars summarising a small list of values.
     *
     * @param g graphics
     * @param r destination rectangle
     * @param labels label strings
     * @param values numeric values
     * @param scale absolute max for the bar height (auto if &lt;= 0)
     */
    static void drawMetricBars(Graphics2D g, Rectangle r, String[] labels, float[] values, float scale) {
        if (labels == null || values == null || labels.length == 0
                || labels.length != values.length || r.width <= 0 || r.height <= 0) {
            drawEmpty(g, r);
            return;
        }
        float s = scale;
        if (s <= 0.0f) {
            for (float v : values) {
                s = Math.max(s, Math.abs(v));
            }
            if (s <= 0.0f) {
                s = 1.0f;
            }
        }
        int count = labels.length;
        int gap = 4;
        int barW = Math.max(8, (r.width - gap * (count + 1)) / count);
        int labelH = 14;
        int trackH = Math.max(20, r.height - labelH - 4);
        int midY = r.y + trackH / 2;
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < count; ++i) {
            int x = r.x + gap + i * (barW + gap);
            g.setColor(WorkbenchTheme.PANEL_SOLID);
            g.fillRect(x, r.y, barW, trackH);
            g.setColor(WorkbenchTheme.LINE);
            g.drawRect(x, r.y, barW, trackH);
            float v = values[i] / s;
            v = clamp(v, -1.0f, 1.0f);
            int barH = (int) Math.round(Math.abs(v) * (trackH / 2.0));
            if (v >= 0.0f) {
                g.setColor(POSITIVE);
                g.fillRect(x + 1, midY - barH, barW - 1, barH);
            } else {
                g.setColor(NEGATIVE);
                g.fillRect(x + 1, midY, barW - 1, barH);
            }
            g.setColor(WorkbenchTheme.MUTED);
            int labelW = fm.stringWidth(labels[i]);
            g.drawString(labels[i], x + Math.max(0, (barW - labelW) / 2), r.y + trackH + 12);
        }
    }

    /**
     * Draws a horizontal labelled bar (value | label) for a single metric.
     *
     * @param g graphics
     * @param r destination rectangle
     * @param value signed value
     * @param scale absolute max (auto if &lt;= 0)
     * @param label label
     */
    static void drawHorizontalBar(Graphics2D g, Rectangle r, float value, float scale, String label) {
        float s = scale;
        if (s <= 0.0f) {
            s = Math.max(1.0f, Math.abs(value));
        }
        g.setColor(WorkbenchTheme.PANEL_SOLID);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        int midX = r.x + r.width / 2;
        g.setColor(WorkbenchTheme.MUTED);
        g.drawLine(midX, r.y, midX, r.y + r.height);
        float v = clamp(value / s, -1.0f, 1.0f);
        int barW = (int) Math.round(Math.abs(v) * (r.width / 2.0));
        if (v >= 0.0f) {
            g.setColor(POSITIVE);
            g.fillRect(midX + 1, r.y + 2, barW, r.height - 4);
        } else {
            g.setColor(NEGATIVE);
            g.fillRect(midX - barW, r.y + 2, barW, r.height - 4);
        }
        if (label != null) {
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            FontMetrics fm = g.getFontMetrics();
            int lw = fm.stringWidth(label);
            g.drawString(label, r.x + r.width - lw - 6, r.y + r.height - 4);
        }
    }

    /**
     * Draws pieces for a position onto a mini board.
     *
     * @param g graphics
     * @param r board rectangle
     * @param fen current position FEN; null draws an empty board
     */
    static void drawPositionPieces(Graphics2D g, Rectangle r, String fen) {
        if (fen == null || fen.isBlank()) {
            return;
        }
        chess.core.Position position;
        try {
            position = new chess.core.Position(fen);
        } catch (IllegalArgumentException ex) {
            return;
        }
        byte[] squares = position.getBoard();
        double cell = r.width / 8.0;
        for (int sq = 0; sq < 64; sq++) {
            byte piece = squares[sq];
            if (piece == chess.core.Piece.EMPTY) {
                continue;
            }
            int file = sq & 7;
            int rank = sq >> 3;
            int drawRank = 7 - rank;
            double x = r.x + file * cell;
            double y = r.y + drawRank * cell;
            chess.images.assets.Shapes.drawPiece(piece, g, x, y, cell, cell);
        }
    }

    /**
     * Draws a 64-cell 8x8 board outline as a backdrop.
     *
     * @param g graphics
     * @param r destination rectangle
     */
    static void drawMiniBoard(Graphics2D g, Rectangle r) {
        double cell = r.width / 8.0;
        for (int rank = 0; rank < 8; ++rank) {
            for (int file = 0; file < 8; ++file) {
                int x = (int) Math.floor(r.x + file * cell);
                int y = (int) Math.floor(r.y + rank * cell);
                int w = (int) Math.ceil(cell + 1);
                int h = (int) Math.ceil(cell + 1);
                boolean light = ((rank + file) & 1) == 0;
                g.setColor(light ? WorkbenchTheme.BOARD_LIGHT : WorkbenchTheme.BOARD_DARK);
                g.fillRect(x, y, w, h);
            }
        }
        g.setColor(WorkbenchTheme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
    }

    /**
     * Overlays a per-square color halo onto a mini board.
     *
     * @param g graphics
     * @param r destination rectangle
     * @param squareValues 64 signed values
     * @param scale absolute max (auto if &lt;= 0)
     * @param flipped if true, rank 0 sits at the bottom
     */
    static void drawSquareOverlay(Graphics2D g, Rectangle r, float[] squareValues, float scale,
            boolean flipped) {
        if (squareValues == null || squareValues.length < 64) {
            return;
        }
        float s = scale;
        if (s <= 0.0f) {
            for (float v : squareValues) {
                s = Math.max(s, Math.abs(v));
            }
            if (s <= 0.0f) {
                s = 1.0f;
            }
        }
        double cell = r.width / 8.0;
        for (int sq = 0; sq < 64; ++sq) {
            int file = sq & 7;
            int rank = sq >> 3;
            int drawRank = flipped ? rank : 7 - rank;
            int x = (int) Math.floor(r.x + file * cell);
            int y = (int) Math.floor(r.y + drawRank * cell);
            int w = (int) Math.ceil(cell + 1);
            int h = (int) Math.ceil(cell + 1);
            float v = squareValues[sq] / s;
            v = clamp(v, -1.0f, 1.0f);
            Color base = signedRamp(v);
            int alpha = Math.min(220, Math.round(Math.abs(v) * 220));
            if (alpha < 12) {
                continue;
            }
            g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
            g.fillRect(x, y, w, h);
        }
    }

    /**
     * Draws coordinate labels around a mini board.
     *
     * @param g graphics
     * @param r board rectangle
     */
    static void drawBoardCoordinates(Graphics2D g, Rectangle r) {
        g.setFont(WorkbenchTheme.font(9, Font.PLAIN));
        g.setColor(WorkbenchTheme.MUTED);
        double cell = r.width / 8.0;
        for (int file = 0; file < 8; ++file) {
            int x = (int) Math.round(r.x + file * cell + cell / 2.0 - 3);
            g.drawString(String.valueOf((char) ('a' + file)), x, r.y + r.height + 11);
        }
        for (int rank = 0; rank < 8; ++rank) {
            int y = (int) Math.round(r.y + rank * cell + cell / 2.0 + 3);
            g.drawString(String.valueOf((char) ('1' + (7 - rank))), r.x - 9, y);
        }
    }

    /**
     * Draws a labelled rectangular block with a fill that scales with activity.
     *
     * @param g graphics
     * @param r destination rectangle
     * @param title primary label
     * @param subtitle subtitle (may be null)
     * @param activity 0..1
     * @param accent accent color
     */
    static void drawAbstractBlock(Graphics2D g, Rectangle r, String title, String subtitle,
            float activity, Color accent) {
        float a = clamp(activity, 0.0f, 1.0f);
        Color fill = lerp(WorkbenchTheme.PANEL_SOLID, accent, 0.18f + 0.55f * a);
        g.setColor(fill);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
        g.setColor(accent);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 8, 8);
        if (title != null) {
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(13, Font.BOLD));
            g.drawString(title, r.x + 10, r.y + 18);
        }
        if (subtitle != null) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            g.drawString(subtitle, r.x + 10, r.y + 34);
        }
        int barY = r.y + r.height - 10;
        int barW = r.width - 20;
        g.setColor(WorkbenchTheme.LINE);
        g.fillRect(r.x + 10, barY, barW, 4);
        g.setColor(accent);
        g.fillRect(r.x + 10, barY, Math.round(barW * a), 4);
    }

    /**
     * Draws an elbow connector from the right edge of `from` to the left edge
     * of `to`, in `color`. `active` thickens the line and adds an arrow head.
     *
     * @param g graphics
     * @param from source rectangle
     * @param to destination rectangle
     * @param color connector color
     * @param active true for active path, false for inactive
     */
    static void drawElbowConnection(Graphics2D g, Rectangle from, Rectangle to, Color color,
            boolean active) {
        int x1 = from.x + from.width;
        int y1 = from.y + from.height / 2;
        int x2 = to.x;
        int y2 = to.y + to.height / 2;
        int midX = (x1 + x2) / 2;
        Color c = active ? color : new Color(color.getRed(), color.getGreen(), color.getBlue(), 90);
        g.setStroke(new BasicStroke(active ? 2.4f : 1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c);
        Path2D path = new Path2D.Double();
        path.moveTo(x1, y1);
        path.lineTo(midX, y1);
        path.lineTo(midX, y2);
        path.lineTo(x2, y2);
        g.draw(path);
        g.setStroke(new BasicStroke(1.0f));
        if (active) {
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(x2 - 7, y2 - 4);
            arrow.lineTo(x2, y2);
            arrow.lineTo(x2 - 7, y2 + 4);
            g.draw(arrow);
        }
    }

    /**
     * Draws a wired connection between two node points (used by NNUE diagram).
     *
     * @param g graphics
     * @param x1 source x
     * @param y1 source y
     * @param x2 destination x
     * @param y2 destination y
     * @param strength signed magnitude in [-1, 1]
     * @param emphasised true to thicken the line
     */
    static void drawWeightedEdge(Graphics2D g, int x1, int y1, int x2, int y2, float strength,
            boolean emphasised) {
        float s = clamp(strength, -1.0f, 1.0f);
        Color base = s >= 0.0f ? POSITIVE : NEGATIVE;
        int alpha = Math.min(255, Math.round(60 + 195 * Math.abs(s)));
        Color c = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
        float width = (emphasised ? 1.8f : 1.0f) + 1.6f * Math.abs(s);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c);
        g.drawLine(x1, y1, x2, y2);
        g.setStroke(new BasicStroke(1.0f));
    }

    /**
     * Draws a single node circle for use in the NNUE wired diagram.
     *
     * @param g graphics
     * @param cx center x
     * @param cy center y
     * @param radius node radius
     * @param value signed activation in [-1, 1]
     * @param selected true when this node is selected
     */
    static void drawNode(Graphics2D g, int cx, int cy, int radius, float value, boolean selected) {
        Color fill = signedRamp(value);
        g.setColor(fill);
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g.setColor(selected ? WorkbenchTheme.ACCENT : WorkbenchTheme.LINE);
        g.setStroke(new BasicStroke(selected ? 2.0f : 1.0f));
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g.setStroke(new BasicStroke(1.0f));
    }

    /**
     * Returns a square label like "e4" for a 0..63 square (LERF: rank 0 = white).
     *
     * @param square 0..63
     * @return square label
     */
    static String squareLabel(int square) {
        int file = square & 7;
        int rank = square >> 3;
        return "" + (char) ('a' + file) + (char) ('1' + rank);
    }

    /**
     * Draws a compact half-KP feature glyph: a mini 8x8 board with the king
     * square highlighted in gold and the piece square highlighted with a
     * piece-letter token. No actual chess art - the letters keep the glyph
     * readable at very small sizes and avoid the white/black ambiguity of
     * half-KP "us/them" sides.
     *
     * @param g graphics
     * @param r rectangle (square recommended)
     * @param kingSquare king square (0..63)
     * @param pieceCode 0=P 1=N 2=B 3=R 4=Q (own side), 5..9 enemy (lowercase)
     * @param pieceSquare piece square (0..63)
     */
    static void drawHalfKpGlyph(Graphics2D g, Rectangle r, int kingSquare,
            int pieceCode, int pieceSquare) {
        drawMiniBoard(g, r);
        drawSquareLetter(g, r, kingSquare, "K", new Color(255, 200, 80, 220));
        char letter = "PNBRQpnbrq".charAt(Math.min(9, Math.max(0, pieceCode)));
        Color tint = pieceCode >= 5
                ? new Color(220, 90, 90, 220)
                : new Color(90, 160, 230, 220);
        if (pieceSquare != kingSquare) {
            drawSquareLetter(g, r, pieceSquare,
                    String.valueOf(Character.toUpperCase(letter)), tint);
        }
    }

    /**
     * Draws a colored cell on a mini board with a single-letter token on top.
     *
     * @param g graphics
     * @param r mini board rectangle
     * @param square 0..63
     * @param letter letter to draw
     * @param fill fill color
     */
    static void drawSquareLetter(Graphics2D g, Rectangle r, int square,
            String letter, Color fill) {
        double cell = r.width / 8.0;
        int file = square & 7;
        int rank = square >> 3;
        int drawRank = 7 - rank;
        int x = (int) Math.floor(r.x + file * cell);
        int y = (int) Math.floor(r.y + drawRank * cell);
        int w = (int) Math.ceil(cell + 1);
        int h = (int) Math.ceil(cell + 1);
        g.setColor(fill);
        g.fillRect(x, y, w, h);
        int fontSize = Math.max(8, (int) (cell * 0.62));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        int lw = fm.stringWidth(letter);
        int la = fm.getAscent();
        g.setColor(new Color(20, 20, 20));
        g.drawString(letter, x + (w - lw) / 2, y + (h + la) / 2 - 2);
    }

    /**
     * Enables high-quality rendering hints.
     *
     * @param g graphics
     */
    static void useHighQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    /**
     * Computes mean / abs-mean / rms / min / max in a single pass.
     *
     * @param values source
     * @return [mean, meanAbs, rms, min, max]
     */
    static float[] summarize(float[] values) {
        if (values == null || values.length == 0) {
            return new float[] { 0, 0, 0, 0, 0 };
        }
        float min = values[0];
        float max = values[0];
        double sum = 0.0;
        double sumAbs = 0.0;
        double sumSq = 0.0;
        for (float v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
            sumAbs += Math.abs(v);
            sumSq += (double) v * v;
        }
        double inv = 1.0 / values.length;
        return new float[] {
                (float) (sum * inv),
                (float) (sumAbs * inv),
                (float) Math.sqrt(sumSq * inv),
                min,
                max,
        };
    }

    /**
     * Returns the index of the maximum absolute value in a slice.
     *
     * @param values source
     * @param from inclusive start
     * @param to exclusive end
     * @return index in [from, to) or from when slice is empty
     */
    static int argMaxAbs(float[] values, int from, int to) {
        int best = from;
        float bestAbs = -1.0f;
        for (int i = from; i < to; ++i) {
            float a = Math.abs(values[i]);
            if (a > bestAbs) {
                bestAbs = a;
                best = i;
            }
        }
        return best;
    }

    /**
     * Clamps a float to a range.
     *
     * @param value value
     * @param min minimum
     * @param max maximum
     * @return clamped value
     */
    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
