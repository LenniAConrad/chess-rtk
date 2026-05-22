package application.gui.workbench.network;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * Stateless atlas geometry and heatmap helpers for {@link NnueView}.
 */
public final class NnueAtlas {

    /**
     * Utility class.
     */
    private NnueAtlas() {
    }

    /**
     * Picks a per-row pixel height for the scrolling atlas. Smaller for
     * very big networks so the user can still scroll the whole thing in
     * a reasonable time.
     *
     * @param hidden hidden-layer dimension
     * @return row height in pixels
     */
    public static int pickAtlasRowHeight(int hidden) {
        if (hidden >= 512) {
            return 24;
        }
        if (hidden >= 256) {
            return 30;
        }
        return 40;
    }

    /**
     * Picks a per-tile pixel width matching the row height.
     *
     * @param hidden hidden-layer dimension
     * @return tile width
     */
    public static int pickAtlasTileWidth(int hidden) {
    return pickAtlasRowHeight(hidden);
    }

    /**
     * Parses the slot index from an atlas tile region title of the form
     * "Slot 12 · own pawn".
     *
     * @param title region title
     * @return slot index or -1 when not parseable
     */
    public static int parseSlotNumber(String title) {
        int start = "Slot ".length();
    return parseFirstInteger(title, start);
    }

    /**
     * Parses the first integer beginning at a title offset.
     *
     * @param title source title
     * @param start start offset
     * @return integer or -1 when not parseable
     */
    public static int parseFirstInteger(String title, int start) {
        int end = start;
        while (end < title.length() && Character.isDigit(title.charAt(end))) {
            end++;
        }
        if (end == start) {
            return -1;
        }
        try {
            return Integer.parseInt(title.substring(start, end));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * Extracts the integer feature index embedded in a hit-region title of
     * the form "Feature #12345  ...".
     *
     * @param title region title
     * @return feature index or -1 when not parseable
     */
    public static int parseFeatureIndex(String title) {
        int hash = title.indexOf('#');
        if (hash < 0) {
            return -1;
        }
        int end = hash + 1;
        while (end < title.length() && Character.isDigit(title.charAt(end))) {
            end++;
        }
        if (end == hash + 1) {
            return -1;
        }
        try {
            return Integer.parseInt(title.substring(hash + 1, end));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * Picks the height for the all-slot atlas strip.
     *
     * @param contentHeight available content height
     * @return strip height
     */
    public static int atlasWholeOverviewHeight(int contentHeight) {
        if (contentHeight < 360) {
            return Math.max(80, contentHeight / 4);
        }
        return Math.min(168, Math.max(112, contentHeight / 4));
    }

    /**
     * Renders all atlas cells into an unscaled pixel image.
     *
     * @param atlas atlas values
     * @param order slot render order
     * @param hidden hidden slot count
     * @param planes plane count
     * @param squares squares per plane
     * @param perNeuronScale scale per hidden slot
     * @return rendered atlas image
     */
    public static java.awt.image.BufferedImage atlasPlaneImage(float[] atlas, Integer[] order,
            int hidden, int planes, int squares, float[] perNeuronScale) {
        int width = Math.max(1, planes * 8);
        int height = Math.max(1, hidden * 8);
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];
        for (int row = 0; row < hidden && row < order.length; row++) {
            int slot = order[row];
            float scale = Math.max(1e-6f, valueAt(perNeuronScale, slot));
            for (int p = 0; p < planes; p++) {
                int offset = (slot * planes + p) * squares;
                for (int sq = 0; sq < 64; sq++) {
                    int file = sq & 7;
                    int rank = sq >> 3;
                    int drawRank = 7 - rank;
                    float v = valueAt(atlas, offset + sq) / scale;
                    if (v > 1.0f) {
                        v = 1.0f;
                    } else if (v < -1.0f) {
                        v = -1.0f;
                    }
                    Color c = atlasRamp(v);
                    int x = p * 8 + file;
                    int y = row * 8 + drawRank;
                    pixels[y * width + x] = (255 << 24)
                            | (c.getRed() << 16)
                            | (c.getGreen() << 8)
                            | c.getBlue();
                }
            }
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    /**
     * Finds a selected slot in the current atlas order.
     *
     * @param order slot render order
     * @param slot selected slot
     * @return index in order, or -1
     */
    public static int orderIndex(Integer[] order, int slot) {
        for (int i = 0; i < order.length; i++) {
            if (order[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Draws one metric row in the atlas explanation pane.
     *
     * @param g graphics context
     * @param bounds drawing bounds
     * @param y baseline row top
     * @param label metric label
     * @param value metric value
     * @param accent value color
     * @return next row y coordinate
     */
    public static int drawAtlasMetricLine(Graphics2D g, Rectangle bounds, int y,
            String label, String value, Color accent) {
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.BOLD));
        g.drawString(label, bounds.x, y + 11);
        g.setColor(accent == null ? Theme.TEXT : accent);
        g.setFont(Theme.font(12, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Ui.elide(value, fm, Math.max(20, bounds.width - 120)),
                bounds.x + 120, y + 12);
        return y + 22;
    }

    /**
     * Paints a composite slot thumbnail using the strongest signed plane value
     * on each square.
     *
     * @param g graphics context
     * @param r tile bounds
     * @param atlas atlas values
     * @param slot hidden slot
     * @param planes plane count
     * @param squares squares per plane
     * @param scale heatmap scale
     */
    public static void paintAtlasCompositeTile(Graphics2D g, Rectangle r, float[] atlas,
            int slot, int planes, int squares, float scale) {
        if (squares != 64) {
            TensorViz.drawEmpty(g, r);
            return;
        }
        float[] composite = new float[64];
        for (int sq = 0; sq < 64; sq++) {
            float best = 0.0f;
            for (int p = 0; p < planes; p++) {
                float value = valueAt(atlas, (slot * planes + p) * squares + sq);
                if (Math.abs(value) > Math.abs(best)) {
                    best = value;
                }
            }
            composite[sq] = best;
        }
        TensorViz.drawHeatmap(g, r, composite, 8, 8, Math.max(1e-6f, scale), true);
    }

    /**
     * Returns a short slot badge for the gallery.
     *
     * @param output output weights
     * @param slot hidden slot
     * @param atlas atlas values
     * @param planes plane count
     * @param squares squares per plane
     * @return badge text
     */
    public static String atlasSlotBadge(float[] output, int slot, float[] atlas, int planes, int squares) {
        if (output != null && slot < output.length) {
            return String.format("%+.2f", output[slot]);
        }
        return String.format("%.2f", atlasSlotMagnitude(atlas, slot, planes, squares));
    }

    /**
     * Returns a detail tooltip string for one atlas slot.
     *
     * @param output output weights
     * @param slot hidden slot
     * @param atlas atlas values
     * @param planes plane count
     * @param squares squares per plane
     * @return detail text
     */
    public static String atlasSlotDetail(float[] output, int slot, float[] atlas, int planes, int squares) {
        return String.format("output %+.3f · magnitude %.3f",
                valueAt(output, slot), atlasSlotMagnitude(atlas, slot, planes, squares));
    }

    /**
     * Heuristic label for a slot based on plane concentration and sparsity.
     *
     * @param atlas atlas values
     * @param slot hidden slot
     * @param planes plane count
     * @param squares squares per plane
     * @return archetype label
     */
    public static String atlasSlotArchetype(float[] atlas, int slot, int planes, int squares) {
        int plane = strongestAtlasPlane(atlas, slot, planes, squares);
        float sparsity = atlasSlotSparsity(atlas, slot, planes, squares);
        String density = sparsity > 0.78f ? "sparse" : sparsity < 0.45f ? "broad" : "focused";
        return density + " · " + (plane >= 0 ? atlasPlaneName(plane, planes) : "mixed");
    }

    /**
     * Returns the slot's mean absolute atlas weight.
     *
     * @param atlas atlas values
     * @param slot hidden slot
     * @param planes plane count
     * @param squares squares per plane
     * @return mean absolute weight
     */
    public static float atlasSlotMagnitude(float[] atlas, int slot, int planes, int squares) {
        int base = slot * planes * squares;
        int span = planes * squares;
        float sum = 0.0f;
        for (int i = 0; i < span; i++) {
            sum += Math.abs(valueAt(atlas, base + i));
        }
        return span == 0 ? 0.0f : sum / span;
    }

    /**
     * Returns approximate sparsity for one slot.
     *
     * @param atlas atlas values
     * @param slot hidden slot
     * @param planes plane count
     * @param squares squares per plane
     * @return sparsity score
     */
    public static float atlasSlotSparsity(float[] atlas, int slot, int planes, int squares) {
        int base = slot * planes * squares;
        int span = planes * squares;
        float max = 1e-6f;
        for (int i = 0; i < span; i++) {
            max = Math.max(max, Math.abs(valueAt(atlas, base + i)));
        }
        float threshold = max * 0.2f;
        int near = 0;
        for (int i = 0; i < span; i++) {
            if (Math.abs(valueAt(atlas, base + i)) < threshold) {
                near++;
            }
        }
        return span == 0 ? 0.0f : near / (float) span;
    }

    /**
     * Returns the plane with most absolute mass for one slot.
     *
     * @param atlas atlas values
     * @param slot hidden slot
     * @param planes plane count
     * @param squares squares per plane
     * @return strongest plane index
     */
    public static int strongestAtlasPlane(float[] atlas, int slot, int planes, int squares) {
        int best = -1;
        float bestMass = -1.0f;
        for (int p = 0; p < planes; p++) {
            float sum = 0.0f;
            int base = (slot * planes + p) * squares;
            for (int sq = 0; sq < squares; sq++) {
                sum += Math.abs(valueAt(atlas, base + sq));
            }
            if (sum > bestMass) {
                bestMass = sum;
                best = p;
            }
        }
        return best;
    }

    /**
     * Returns the strongest positive or negative square in one plane.
     *
     * @param data flat plane data
     * @param offset plane offset
     * @param squares square count
     * @param positive true for strongest positive, false for strongest negative
     * @return strongest square index
     */
    public static int strongestSquare(float[] data, int offset, int squares, boolean positive) {
        int best = -1;
        float bestValue = positive ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        for (int sq = 0; sq < squares; sq++) {
            float value = valueAt(data, offset + sq);
            if (positive ? value > bestValue : value < bestValue) {
                bestValue = value;
                best = sq;
            }
        }
        return best;
    }

    /**
     * Formats one square/value pair.
     *
     * @param data flat data
     * @param offset data offset
     * @param square selected square
     * @return formatted square value
     */
    public static String squareValueLabel(float[] data, int offset, int square) {
        if (square < 0) {
            return "-";
        }
        return TensorViz.squareLabel(square) + " " + String.format("%+.3f", valueAt(data, offset + square));
    }

    /**
     * Formats the selected-square value.
     *
     * @param data flat data
     * @param offset data offset
     * @param square selected square
     * @return formatted selected-square value
     */
    public static String selectedSquareValue(float[] data, int offset, int square) {
        return square < 0 ? "-" : String.format("%s %+.3f",
                TensorViz.squareLabel(square), valueAt(data, offset + square));
    }

    /**
     * Computes per-neuron max-abs scale for the atlas.
     *
     * @param data atlas data (or diff data)
     * @param hidden hidden size
     * @param planes piece planes
     * @param squares squares per plane
     * @return per-neuron scale array
     */
    public static float[] computePerNeuronScale(float[] data, int hidden, int planes, int squares) {
        float[] out = new float[hidden];
        int span = planes * squares;
        for (int h = 0; h < hidden; h++) {
            float m = 0.0f;
            int b = h * span;
            int end = b + span;
            for (int i = b; i < end; i++) {
                float a = Math.abs(data[i]);
                if (a > m) {
                    m = a;
                }
            }
            out[h] = Math.max(1e-6f, m);
        }
        return out;
    }

    /**
     * Returns total absolute sensitivity to one board square across planes.
     *
     * @param atlas atlas values
     * @param slot hidden slot
     * @param planes plane count
     * @param squares squares per plane
     * @param square board square
     * @return focus magnitude
     */
    public static float atlasSquareFocus(float[] atlas, int slot, int planes, int squares, int square) {
        float sum = 0.0f;
        for (int p = 0; p < planes; p++) {
            sum += Math.abs(valueAt(atlas, (slot * planes + p) * squares + square));
        }
        return sum;
    }

    /**
     * Lightly outlines one board square inside an atlas tile so the user can
     * see which cell maps to the currently-selected board square.
     *
     * @param g graphics
     * @param tile tile rectangle
     * @param square 0..63 LERF index
     */
    public static void overlaySelectedSquare(Graphics2D g, Rectangle tile, int square) {
        if (tile.width < 8 || tile.height < 8) {
            return;
        }
        int file = square & 7;
        int rank = square >> 3;
        int drawRank = 7 - rank;
        double cw = tile.width / 8.0;
        double ch = tile.height / 8.0;
        int cx = (int) Math.floor(tile.x + file * cw);
        int cy = (int) Math.floor(tile.y + drawRank * ch);
        int cw2 = (int) Math.ceil(cw + 1);
        int ch2 = (int) Math.ceil(ch + 1);
        g.setColor(TensorViz.FOCUS);
        g.drawRect(cx, cy, cw2 - 1, ch2 - 1);
    }

    /**
     * Returns the short column label for an atlas piece plane.
     *
     * @param plane plane index (0..planes-1)
     * @param totalPlanes total number of piece planes
     * @return short label
     */
    public static String atlasPlaneLabel(int plane, int totalPlanes) {
        char[] own = { 'P', 'N', 'B', 'R', 'Q' };
        char[] enemy = { 'p', 'n', 'b', 'r', 'q' };
        if (plane < 5) {
            return String.valueOf(own[plane]);
        }
        if (plane < 10) {
            return String.valueOf(enemy[plane - 5]);
        }
        return totalPlanes > 10 ? "K" : "";
    }

    /**
     * Returns the long human-readable name for an atlas piece plane.
     *
     * @param plane plane index
     * @param totalPlanes total piece planes
     * @return long name
     */
    public static String atlasPlaneName(int plane, int totalPlanes) {
        String[] own = { "own pawn", "own knight", "own bishop", "own rook", "own queen" };
        String[] enemy = { "enemy pawn", "enemy knight", "enemy bishop", "enemy rook", "enemy queen" };
        if (plane < 5) {
            return own[plane];
        }
        if (plane < 10) {
            return enemy[plane - 5];
        }
        return totalPlanes > 10 ? "kings" : ("plane " + plane);
    }

    /**
     * Paints a single dense atlas tile straight onto the canvas with no
     * border, reading 64 weights from a flat atlas array starting at the
     * given offset.
     *
     * @param g graphics
     * @param r tile rectangle
     * @param atlas flat atlas data
     * @param offset starting offset of this tile's 64 weights
     * @param scale max-abs scale
     * @param selected highlight ring when selected
     */
    public static void paintAtlasTileDense(Graphics2D g, Rectangle r, float[] atlas,
            int offset, float scale, boolean selected) {
        double cw = r.width / 8.0;
        double ch = r.height / 8.0;
        for (int sq = 0; sq < 64; sq++) {
            int file = sq & 7;
            int rank = sq >> 3;
            int drawRank = 7 - rank;
            int cellX = (int) Math.floor(r.x + file * cw);
            int cellY = (int) Math.floor(r.y + drawRank * ch);
            int cellW = (int) Math.ceil(cw + 1);
            int cellH = (int) Math.ceil(ch + 1);
            float v = atlas[offset + sq] / scale;
            if (v > 1.0f) {
                v = 1.0f;
            } else if (v < -1.0f) {
                v = -1.0f;
            }
            g.setColor(atlasRamp(v));
            g.fillRect(cellX, cellY, cellW, cellH);
        }
        if (selected) {
            g.setColor(TensorViz.FOCUS);
            g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            g.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1);
        }
    }

    /**
     * Atlas colormap: signed weights in [-1, 1] mapped to the shared
     * green/red diverging ramp used elsewhere (raise = green, lower = red),
     * on the workbench's light heat-zero background.
     *
     * @param v normalised value in [-1, 1]
     * @return colour
     */
    public static java.awt.Color atlasRamp(float v) {
        return TensorViz.signedRamp(v);
    }

    /**
     * Returns array value or zero when out of range.
     *
     * @param values source array
     * @param index index
     * @return value or zero
     */
    private static float valueAt(float[] values, int index) {
        return values == null || index < 0 || index >= values.length ? 0.0f : values[index];
    }
}
