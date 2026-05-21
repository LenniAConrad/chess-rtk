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

    // The network-view accent palette lives in WorkbenchTheme so the NN
    // visualizers share one colour language with the rest of the workbench.
    // These aliases keep the existing WorkbenchTensorViz.* call sites terse.

    /**
     * Positive-activation accent (used for "up" / gain).
     */
    static final Color POSITIVE = WorkbenchTheme.NN_POSITIVE;

    /**
     * Negative-activation accent (used for "down" / loss).
     */
    static final Color NEGATIVE = WorkbenchTheme.NN_NEGATIVE;

    /**
     * Trunk / data-flow accent.
     */
    static final Color TRUNK = WorkbenchTheme.NN_TRUNK;

    /**
     * Policy-branch accent.
     */
    static final Color POLICY = WorkbenchTheme.NN_POLICY;

    /**
     * Value-branch accent.
     */
    static final Color VALUE = WorkbenchTheme.NN_VALUE;

    /**
     * Neutral-fill background for cells with no signal.
     */
    static final Color NEUTRAL = WorkbenchTheme.NN_NEUTRAL;

    /**
     * Lightest heatmap fill (signed scale, near zero).
     */
    static final Color HEAT_ZERO = WorkbenchTheme.NN_HEAT_ZERO;

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
        g.setColor(WorkbenchTheme.PANEL_SOLID);
        g.fillRoundRect(r.x, r.y, r.width, r.height,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        g.setColor(WorkbenchTheme.ACCENT);
        g.fillRoundRect(r.x, r.y, 4, r.height,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(14, Font.BOLD));
        g.drawString(title, r.x + 12, r.y + 18);
        if (subtitle != null && !subtitle.isEmpty()) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(WorkbenchUi.elide(subtitle, fm, Math.max(16, r.width - 24)),
                    r.x + 12, r.y + 34);
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
        g.fillRoundRect(r.x, r.y, r.width, r.height,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        if (accent != null) {
            g.setColor(accent);
            g.fillRoundRect(r.x, r.y, 4, r.height,
                    WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        }
        if (title != null) {
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(12, Font.BOLD));
            g.drawString(title, r.x + 10, r.y + 15);
        }
        if (subtitle != null) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(WorkbenchUi.elide(subtitle, fm, Math.max(16, r.width - 20)),
                    r.x + 10, r.y + 29);
        }
    }

    /**
     * Draws a compact readout chip for "important now" style summaries.
     *
     * @param g graphics
     * @param r chip rectangle
     * @param label small label
     * @param value main value
     * @param accent accent colour
     */
    static void drawInfoChip(Graphics2D g, Rectangle r, String label, String value, Color accent) {
        if (r.width <= 12 || r.height <= 12) {
            return;
        }
        g.setColor(WorkbenchTheme.ELEVATED_SOLID);
        g.fillRoundRect(r.x, r.y, r.width, r.height,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        if (accent != null) {
            g.setColor(accent);
            g.fillRoundRect(r.x, r.y, 4, r.height,
                    WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        }
        int split = Math.min(70, Math.max(42, r.width / 3));
        g.setFont(WorkbenchTheme.font(9, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(WorkbenchTheme.MUTED);
        g.drawString(WorkbenchUi.elide(label, fm, Math.max(12, split - 10)),
                r.x + 9, r.y + Math.max(13, r.height / 2 + 3));
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        fm = g.getFontMetrics();
        g.setColor(WorkbenchTheme.TEXT);
        g.drawString(WorkbenchUi.elide(value, fm, Math.max(12, r.width - split - 8)),
                r.x + split, r.y + Math.max(13, r.height / 2 + 3));
    }

    /**
     * Draws a double-outline marker around a board square.
     *
     * @param g graphics
     * @param board board rectangle
     * @param square 0..63 square
     * @param color marker colour
     */
    static void drawBoardSquareRing(Graphics2D g, Rectangle board, int square, Color color) {
        if (square < 0 || square >= 64 || board.width <= 0 || board.height <= 0) {
            return;
        }
        int file = square & 7;
        int rank = square >> 3;
        int drawRank = 7 - rank;
        double cellW = board.width / 8.0;
        double cellH = board.height / 8.0;
        int x = (int) Math.floor(board.x + file * cellW);
        int y = (int) Math.floor(board.y + drawRank * cellH);
        int w = Math.max(2, (int) Math.ceil(cellW));
        int h = Math.max(2, (int) Math.ceil(cellH));
        g.setColor(WorkbenchTheme.withAlpha(Color.WHITE, 210));
        g.drawRect(x + 1, y + 1, Math.max(1, w - 3), Math.max(1, h - 3));
        g.setColor(color);
        g.drawRect(x + 2, y + 2, Math.max(1, w - 5), Math.max(1, h - 5));
        if (w > 12 && h > 12) {
            g.drawRect(x + 3, y + 3, Math.max(1, w - 7), Math.max(1, h - 7));
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
     * Fast sequential heatmap with a sqrt-gamma ramp, used by the dense raw
     * atlases. Renders into a {@code cols x rows} ARGB bitmap and blits it
     * once, so a mosaic of thousands of channels or attention heads does not
     * turn every repaint into millions of individual fill calls.
     *
     * @param g graphics
     * @param r destination rectangle
     * @param data flat row-major values
     * @param cols column count
     * @param rows row count
     * @param scale absolute max for the colour ramp
     */
    static void drawGammaHeatmap(Graphics2D g, Rectangle r, float[] data, int cols, int rows,
            float scale) {
        if (data == null || cols <= 0 || rows <= 0 || data.length < cols * rows
                || r.width <= 0 || r.height <= 0) {
            return;
        }
        float s = scale <= 0.0f ? 1.0f : scale;
        Color base = WorkbenchTheme.ACCENT;
        int rgb = (base.getRed() << 16) | (base.getGreen() << 8) | base.getBlue();
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(cols, rows, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[cols * rows];
        for (int i = 0; i < pixels.length; ++i) {
            float v = Math.min(1.0f, Math.abs(data[i]) / s);
            int alpha = Math.round(255.0f * (float) Math.sqrt(v));
            pixels[i] = (alpha << 24) | rgb;
        }
        image.setRGB(0, 0, cols, rows, pixels, 0, cols);
        Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, r.x, r.y, r.width, r.height, null);
        if (previous != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
        }
    }

    /**
     * Draws the shared value-head card: a segmented win / draw / loss bar plus
     * the raw value scalar. Used by both the CNN and BT4 overviews so the two
     * architectures present their value output identically.
     *
     * @param g graphics
     * @param r card rectangle
     * @param wdl win / draw / loss probabilities (may be null)
     * @param valueScalar optional single-element value scalar (may be null)
     */
    static void drawWdlCard(Graphics2D g, Rectangle r, float[] wdl, float[] valueScalar) {
        drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "value head — win / draw / loss",
                "predicted game outcome for the side to move");
        int barTop = r.y + 50;
        int barH = Math.min(34, Math.max(20, r.height - 80));
        Rectangle bar = new Rectangle(r.x + 8, barTop, Math.max(1, r.width - 16), barH);
        if (wdl != null && wdl.length >= 3) {
            float w = Math.max(0.0f, wdl[0]);
            float d = Math.max(0.0f, wdl[1]);
            float l = Math.max(0.0f, wdl[2]);
            float sum = Math.max(1e-4f, w + d + l);
            w /= sum;
            d /= sum;
            l /= sum;
            int ww = Math.round(bar.width * w);
            int dw = Math.round(bar.width * d);
            int lw = Math.max(0, bar.width - ww - dw);
            g.setColor(new Color(56, 158, 90));
            g.fillRect(bar.x, bar.y, ww, bar.height);
            g.setColor(new Color(150, 156, 163));
            g.fillRect(bar.x + ww, bar.y, dw, bar.height);
            g.setColor(new Color(201, 74, 74));
            g.fillRect(bar.x + ww + dw, bar.y, lw, bar.height);
            g.setColor(WorkbenchTheme.LINE);
            g.drawRect(bar.x, bar.y, bar.width - 1, bar.height - 1);
            g.setFont(WorkbenchTheme.font(11, Font.BOLD));
            g.setColor(Color.WHITE);
            drawSegmentLabel(g, "W " + Math.round(w * 100) + "%", bar.x, bar.y, ww, bar.height);
            drawSegmentLabel(g, "D " + Math.round(d * 100) + "%", bar.x + ww, bar.y, dw, bar.height);
            drawSegmentLabel(g, "L " + Math.round(l * 100) + "%", bar.x + ww + dw, bar.y, lw, bar.height);
        } else {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            g.drawString("No W/D/L output in this snapshot.", bar.x, bar.y + 16);
        }
        if (valueScalar != null && valueScalar.length > 0) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            g.drawString(String.format("value scalar %+.3f  (+1 = winning, -1 = losing)", valueScalar[0]),
                    bar.x, bar.y + bar.height + 22);
        }
    }

    /**
     * Draws a centred label inside a WDL bar segment when it is wide enough.
     *
     * @param g graphics
     * @param text label text
     * @param x segment x
     * @param y segment y
     * @param width segment width
     * @param height segment height
     */
    private static void drawSegmentLabel(Graphics2D g, String text, int x, int y, int width, int height) {
        FontMetrics fm = g.getFontMetrics();
        if (width < fm.stringWidth(text) + 8) {
            return;
        }
        g.drawString(text, x + (width - fm.stringWidth(text)) / 2,
                y + (height + fm.getAscent() - fm.getDescent()) / 2);
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
     * Returns a diverging green/red color for a signed value in [-1, 1].
     * Matches the "raise eval = green / lower eval = red" convention used
     * everywhere else in the workbench (impact bars, accumulator, atlas).
     *
     * @param value signed value
     * @return color
     */
    static Color signedRamp(float value) {
        float v = clamp(value, -1.0f, 1.0f);
        if (v >= 0.0f) {
            return lerp(HEAT_ZERO, POSITIVE, v);
        }
        return lerp(HEAT_ZERO, NEGATIVE, -v);
    }

    /**
     * Returns a sequential color for a value in [0, 1] using the workbench
     * accent so single-sided magnitudes match the rest of the UI chrome.
     *
     * @param value sequential value
     * @return color
     */
    static Color sequentialRamp(float value) {
        float v = clamp(value, 0.0f, 1.0f);
        return lerp(HEAT_ZERO, WorkbenchTheme.ACCENT, v);
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
        for (int positionIndex = 0; positionIndex < 64; positionIndex++) {
            byte piece = squares[positionIndex];
            if (piece == chess.core.Piece.EMPTY) {
                continue;
            }
            int file = positionIndex & 7;
            int drawRank = positionIndex >>> 3;
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
        g.fillRoundRect(r.x, r.y, r.width, r.height, 3, 3);
        g.setColor(accent);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 3, 3);
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
        drawSquareLetter(g, r, kingSquare, "K",
                WorkbenchTheme.withAlpha(WorkbenchTheme.ACCENT, 200));
        char letter = "PNBRQpnbrq".charAt(Math.min(9, Math.max(0, pieceCode)));
        Color tint = pieceCode >= 5
                ? WorkbenchTheme.withAlpha(NEGATIVE, 200)
                : WorkbenchTheme.withAlpha(POSITIVE, 200);
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
        g.setFont(WorkbenchTheme.font(fontSize, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        int lw = fm.stringWidth(letter);
        int la = fm.getAscent();
        g.setColor(WorkbenchTheme.TEXT);
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
