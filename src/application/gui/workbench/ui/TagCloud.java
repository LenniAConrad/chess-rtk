package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.ToolTipManager;

/**
 * Wrap-layout social tag cloud for position tags. Tags are grouped by their
 * category prefix, such as {@code FACT}, {@code META}, {@code MOVE}, and
 * {@code PIECE}, then rendered as compact theme-aware chips.
 */
public final class TagCloud extends JComponent implements Scrollable {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Display density presets for shared tag surfaces.
     */
    public enum Mode {
        /**
         * Full categorized cloud for the Analyze / Tags tab.
         */
        FULL,

        /**
         * Compact chip preview for dashboard cards.
         */
        COMPACT
    }

    /**
     * Outer content padding.
     */
    private static final int PAD = 10;

    /**
     * Gap between adjacent chips.
     */
    private static final int CHIP_GAP = 6;

    /**
     * Gap between chip rows.
     */
    private static final int ROW_GAP = 6;

    /**
     * Height of one chip.
     */
    private static final int CHIP_HEIGHT = 23;

    /**
     * Height reserved for a group heading.
     */
    private static final int HEADING_HEIGHT = 18;

    /**
     * Vertical gap between groups.
     */
    private static final int GROUP_GAP = 11;

    /**
     * Maximum chip text width in full mode.
     */
    private static final int FULL_TEXT_WIDTH = 320;

    /**
     * Maximum chip text width in compact mode.
     */
    private static final int COMPACT_TEXT_WIDTH = 145;

    /**
     * Preferred viewport width in full mode.
     */
    private static final int FULL_WIDTH = 520;

    /**
     * Preferred viewport width in compact mode.
     */
    private static final int COMPACT_WIDTH = 260;

    /**
     * Preferred fixed preview height in compact mode.
     */
    private static final int COMPACT_HEIGHT = 68;

    /**
     * Tag view mode.
     */
    private final Mode mode;

    /**
     * Parsed tag items in source order.
     */
    private List<TagItem> tags = List.of();

    /**
     * Creates a full tag cloud.
     */
    public TagCloud() {
        this(Mode.FULL);
    }

    /**
     * Creates a tag cloud with the requested display mode.
     *
     * @param mode display mode
     */
    public TagCloud(Mode mode) {
        this.mode = mode == null ? Mode.FULL : mode;
        setOpaque(false);
        setFont(Theme.font(11, Font.PLAIN));
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    /**
     * Sets the raw tag list to display.
     *
     * @param values raw tag values
     */
    public void setTags(List<String> values) {
        tags = parseTags(values);
        revalidate();
        repaint();
    }

    /**
     * Returns only the tag below the cursor as tooltip text.
     *
     * @param event mouse location
     * @return hovered tag text, or null outside chips
     */
    @Override
    public String getToolTipText(MouseEvent event) {
        if (event == null || tags.isEmpty()) {
            return null;
        }
        TagItem item = tagAt(event.getX(), event.getY());
        if (item == null) {
            return null;
        }
        // HTML tooltip with category meaning + raw payload so hovering a
        // chip explains what it represents, not just what it says. Plain
        // tooltips before this change repeated the raw text already
        // visible on the chip.
        String category = item.category();
        String meaning = categoryMeaning(category);
        StringBuilder tip = new StringBuilder("<html><body style='width:240px'>");
        tip.append("<b>").append(category).append("</b>")
                .append(" &nbsp; ").append("<span style='color:#888'>")
                .append(escapeHtml(meaning)).append("</span><br>");
        tip.append(escapeHtml(item.raw())).append("</body></html>");
        return tip.toString();
    }

    /**
     * Returns a one-line description of what a tag category captures, used
     * in hover tooltips so the workbench surfaces meaning without forcing
     * the user to memorise the tag taxonomy.
     *
     * @param category uppercase category name
     * @return short human description
     */
    private static String categoryMeaning(String category) {
        return switch (category == null ? "" : category.toUpperCase(Locale.ROOT)) {
            case "FACT" -> "static observation derived from the position";
            case "META" -> "position metadata such as side to move or game status";
            case "MOVE" -> "property of the next or last move";
            case "MATERIAL" -> "material balance / imbalance";
            case "OPENING" -> "opening classification";
            case "PIECE" -> "individual piece role or placement";
            case "MOBILITY" -> "available squares and reach";
            case "DEVELOPMENT" -> "piece development state";
            case "TACTIC" -> "tactical motif or pattern";
            case "THREAT" -> "active or latent threat";
            case "CHECK" -> "check / check-related condition";
            case "ERROR" -> "error or anomaly";
            case "INFO" -> "informational annotation";
            case "SPACE" -> "spatial control";
            case "STATUS" -> "position evaluation status";
            default -> "tag category";
        };
    }

    /**
     * Tiny HTML escaper used by tag tooltips.
     *
     * @param text raw text
     * @return HTML-safe text
     */
    private static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Returns the number of tags currently rendered.
     *
     * @return tag count
     */
    public int tagCount() {
        return tags.size();
    }

    /**
     * Returns the preferred cloud size.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        int width = availableWidth();
        int height = mode == Mode.COMPACT
                ? COMPACT_HEIGHT
                : Math.max(96, layoutHeight(width));
        return new Dimension(width, height);
    }

    /**
     * Returns the minimum cloud size.
     *
     * @return minimum size
     */
    @Override
    public Dimension getMinimumSize() {
        return mode == Mode.COMPACT
                ? new Dimension(160, COMPACT_HEIGHT)
                : new Dimension(220, 96);
    }

    /**
     * Returns the maximum cloud size.
     *
     * @return maximum size
     */
    @Override
    public Dimension getMaximumSize() {
        return mode == Mode.COMPACT
                ? new Dimension(Integer.MAX_VALUE, COMPACT_HEIGHT)
                : super.getMaximumSize();
    }

    /**
     * Returns the preferred scroll viewport size.
     *
     * @return preferred scroll viewport size
     */
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return mode == Mode.COMPACT
                ? new Dimension(COMPACT_WIDTH, COMPACT_HEIGHT)
                : new Dimension(FULL_WIDTH, 260);
    }

    /**
     * Returns the scroll unit increment.
     *
     * @param visibleRect visible rectangle
     * @param orientation scroll orientation
     * @param direction scroll direction
     * @return unit increment
     */
    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return CHIP_HEIGHT + ROW_GAP;
    }

    /**
     * Returns the scroll block increment.
     *
     * @param visibleRect visible rectangle
     * @param orientation scroll orientation
     * @param direction scroll direction
     * @return block increment
     */
    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return ScrollableSupport.verticalBlockIncrement(visibleRect, CHIP_HEIGHT + ROW_GAP, CHIP_HEIGHT);
    }

    /**
     * Tracks viewport width so chips reflow horizontally.
     *
     * @return true
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    /**
     * Allows vertical scrolling in full mode.
     *
     * @return true only in compact mode
     */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return mode == Mode.COMPACT;
    }

    /**
     * Paints the tag cloud.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            installQuality(g);
            if (tags.isEmpty()) {
                paintEmpty(g);
                return;
            }
            paintGroups(g);
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints all grouped tags.
     *
     * @param g graphics context
     */
    private void paintGroups(Graphics2D g) {
        g.setFont(Theme.font(11, Font.PLAIN));
        FontMetrics metrics = g.getFontMetrics();
        int maxWidth = Math.max(120, getWidth() - PAD * 2);
        int y = PAD;
        int painted = 0;
        for (Map.Entry<String, List<TagItem>> entry : groupedTags().entrySet()) {
            if (painted >= maxPaintedTags()) {
                break;
            }
            CategoryStyle style = styleFor(entry.getKey());
            if (mode == Mode.FULL) {
                paintHeading(g, entry.getKey(), entry.getValue().size(), style, y);
                y += HEADING_HEIGHT;
            }
            int x = PAD;
            for (TagItem item : entry.getValue()) {
                if (painted >= maxPaintedTags()) {
                    break;
                }
                int chipWidth = chipWidth(metrics, item, maxWidth);
                if (x > PAD && x + chipWidth > PAD + maxWidth) {
                    x = PAD;
                    y += CHIP_HEIGHT + ROW_GAP;
                }
                if (mode == Mode.COMPACT && y + CHIP_HEIGHT > getHeight() - PAD) {
                    paintMore(g, tags.size() - painted, x, y, maxWidth);
                    return;
                }
                paintChip(g, metrics, item, style, x, y, chipWidth);
                x += chipWidth + CHIP_GAP;
                painted++;
            }
            y += CHIP_HEIGHT + GROUP_GAP;
        }
        if (painted < tags.size()) {
            paintMore(g, tags.size() - painted, PAD, Math.min(getHeight() - CHIP_HEIGHT - 2, y), maxWidth);
        }
    }

    /**
     * Paints the empty-state copy.
     *
     * @param g graphics context
     */
    private void paintEmpty(Graphics2D g) {
        Ui.paintEmptyState(g, new java.awt.Rectangle(0, 0, getWidth(), getHeight()),
                "No tags yet", "Tags from the active position appear here.");
    }

    /**
     * Paints a category heading.
     *
     * @param g graphics context
     * @param category category label
     * @param count group tag count
     * @param style category style
     * @param y heading y
     */
    private static void paintHeading(Graphics2D g, String category, int count, CategoryStyle style, int y) {
        g.setFont(Theme.font(10, Font.BOLD));
        g.setColor(style.text());
        g.drawString(category.toLowerCase(Locale.ROOT), PAD, y + 11);
        g.setFont(Theme.font(10, Font.PLAIN));
        g.setColor(Theme.MUTED);
        g.drawString(Integer.toString(count), PAD + 58, y + 11);
    }

    /**
     * Paints one tag chip.
     *
     * @param g graphics context
     * @param metrics font metrics
     * @param item tag item
     * @param style category style
     * @param x chip x
     * @param y chip y
     * @param width chip width
     */
    private static void paintChip(Graphics2D g, FontMetrics metrics, TagItem item,
            CategoryStyle style, int x, int y, int width) {
        int arc = 8;
        g.setColor(style.fill());
        g.fillRoundRect(x, y, width, CHIP_HEIGHT, arc, arc);
        g.setColor(style.border());
        g.drawRoundRect(x, y, width - 1, CHIP_HEIGHT - 1, arc, arc);

        int badgeWidth = Math.max(30, metrics.stringWidth(item.category()) + 12);
        g.setColor(style.badge());
        g.fillRoundRect(x + 3, y + 3, badgeWidth, CHIP_HEIGHT - 6, 6, 6);
        g.setColor(style.badgeText());
        g.setFont(Theme.font(9, Font.BOLD));
        FontMetrics badgeMetrics = g.getFontMetrics();
        g.drawString(item.category(), x + 3 + (badgeWidth - badgeMetrics.stringWidth(item.category())) / 2,
                y + 15);

        g.setFont(Theme.font(11, Font.PLAIN));
        g.setColor(Theme.TEXT);
        int textX = x + badgeWidth + 10;
        int textWidth = Math.max(0, width - badgeWidth - 18);
        g.drawString(Ui.elide(item.display(), metrics, textWidth), textX, y + 15);
    }

    /**
     * Paints the compact overflow chip.
     *
     * @param g graphics context
     * @param remaining number of hidden tags
     * @param x chip x
     * @param y chip y
     * @param maxWidth row width
     */
    private static void paintMore(Graphics2D g, int remaining, int x, int y, int maxWidth) {
        if (remaining <= 0) {
            return;
        }
        String label = "+" + remaining + " more";
        g.setFont(Theme.font(10, Font.BOLD));
        FontMetrics metrics = g.getFontMetrics();
        int width = Math.min(maxWidth, metrics.stringWidth(label) + 18);
        if (x + width > PAD + maxWidth) {
            x = PAD;
            y += CHIP_HEIGHT + ROW_GAP;
        }
        g.setColor(Theme.ELEVATED_SOLID);
        g.fillRoundRect(x, y, width, CHIP_HEIGHT, 8, 8);
        g.setColor(Theme.LINE);
        g.drawRoundRect(x, y, width - 1, CHIP_HEIGHT - 1, 8, 8);
        g.setColor(Theme.MUTED);
        g.drawString(label, x + 9, y + 15);
    }

    /**
     * Computes the preferred height for the current width.
     *
     * @param width component width
     * @return preferred height
     */
    private int layoutHeight(int width) {
        if (tags.isEmpty()) {
            return 48;
        }
        FontMetrics metrics = getFontMetrics(Theme.font(11, Font.PLAIN));
        int maxWidth = Math.max(120, width - PAD * 2);
        int y = PAD;
        for (Map.Entry<String, List<TagItem>> entry : groupedTags().entrySet()) {
            y += HEADING_HEIGHT;
            int x = PAD;
            for (TagItem item : entry.getValue()) {
                int chipWidth = chipWidth(metrics, item, maxWidth);
                if (x > PAD && x + chipWidth > PAD + maxWidth) {
                    x = PAD;
                    y += CHIP_HEIGHT + ROW_GAP;
                }
                x += chipWidth + CHIP_GAP;
            }
            y += CHIP_HEIGHT + GROUP_GAP;
        }
        return y + PAD;
    }

    /**
     * Finds the chip item at a component coordinate.
     *
     * @param pointX x coordinate
     * @param pointY y coordinate
     * @return hovered tag item, or null
     */
    private TagItem tagAt(int pointX, int pointY) {
        FontMetrics metrics = getFontMetrics(Theme.font(11, Font.PLAIN));
        int maxWidth = Math.max(120, availableWidth() - PAD * 2);
        int y = PAD;
        int painted = 0;
        for (Map.Entry<String, List<TagItem>> entry : groupedTags().entrySet()) {
            if (painted >= maxPaintedTags()) {
                break;
            }
            if (mode == Mode.FULL) {
                y += HEADING_HEIGHT;
            }
            int x = PAD;
            for (TagItem item : entry.getValue()) {
                if (painted >= maxPaintedTags()) {
                    break;
                }
                int chipWidth = chipWidth(metrics, item, maxWidth);
                if (x > PAD && x + chipWidth > PAD + maxWidth) {
                    x = PAD;
                    y += CHIP_HEIGHT + ROW_GAP;
                }
                if (mode == Mode.COMPACT && y + CHIP_HEIGHT > getHeight() - PAD) {
                    return null;
                }
                Rectangle bounds = new Rectangle(x, y, chipWidth, CHIP_HEIGHT);
                if (bounds.contains(pointX, pointY)) {
                    return item;
                }
                x += chipWidth + CHIP_GAP;
                painted++;
            }
            y += CHIP_HEIGHT + GROUP_GAP;
        }
        return null;
    }

    /**
     * Computes one chip width.
     *
     * @param metrics active font metrics
     * @param item tag item
     * @param maxWidth row width
     * @return chip width
     */
    private int chipWidth(FontMetrics metrics, TagItem item, int maxWidth) {
        int bodyLimit = mode == Mode.COMPACT ? COMPACT_TEXT_WIDTH : FULL_TEXT_WIDTH;
        int bodyWidth = Math.min(bodyLimit, metrics.stringWidth(item.display()));
        int badgeWidth = Math.max(30, metrics.stringWidth(item.category()) + 12);
        return Math.min(maxWidth, badgeWidth + bodyWidth + 22);
    }

    /**
     * Returns the available layout width.
     *
     * @return width in pixels
     */
    private int availableWidth() {
        if (getParent() instanceof javax.swing.JViewport viewport && viewport.getWidth() > 0) {
            return viewport.getWidth();
        }
        if (getWidth() > 0) {
            return getWidth();
        }
        return mode == Mode.COMPACT ? COMPACT_WIDTH : FULL_WIDTH;
    }

    /**
     * Returns maximum tags painted in the current mode.
     *
     * @return maximum tag count
     */
    private int maxPaintedTags() {
        return mode == Mode.COMPACT ? 10 : Integer.MAX_VALUE;
    }

    /**
     * Groups tags by category in source-order category order.
     *
     * @return grouped tags
     */
    private Map<String, List<TagItem>> groupedTags() {
        Map<String, List<TagItem>> groups = new LinkedHashMap<>();
        for (TagItem tag : tags) {
            groups.computeIfAbsent(tag.category(), key -> new ArrayList<>()).add(tag);
        }
        return groups;
    }

    /**
     * Parses raw tags.
     *
     * @param values raw tags
     * @return parsed tag items
     */
    private static List<TagItem> parseTags(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<TagItem> parsed = new ArrayList<>();
        for (String value : values) {
            TagItem item = parseTag(value);
            if (item != null) {
                parsed.add(item);
            }
        }
        return List.copyOf(parsed);
    }

    /**
     * Parses one raw tag.
     *
     * @param value raw tag
     * @return parsed tag item, or null when blank
     */
    private static TagItem parseTag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("tagging failed")) {
            return new TagItem("ERROR", bodyAfterColon(trimmed), trimmed);
        }
        if (trimmed.equalsIgnoreCase("calculating...")) {
            return new TagItem("STATUS", "calculating", trimmed);
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            String prefix = trimmed.substring(0, colon).trim();
            if (isCategory(prefix)) {
                return new TagItem(prefix.toUpperCase(Locale.ROOT), bodyAfterColon(trimmed), trimmed);
            }
        }
        return new TagItem("INFO", compactBody(trimmed), trimmed);
    }

    /**
     * Returns the text after the first colon.
     *
     * @param value source text
     * @return body text
     */
    private static String bodyAfterColon(String value) {
        int colon = value.indexOf(':');
        if (colon < 0 || colon + 1 >= value.length()) {
            return compactBody(value);
        }
        return compactBody(value.substring(colon + 1).trim());
    }

    /**
     * Checks whether a prefix looks like a tag category.
     *
     * @param prefix prefix text
     * @return true when the prefix is a category
     */
    private static boolean isCategory(String prefix) {
        if (prefix.isBlank() || prefix.length() > 24) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            char ch = prefix.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '-') {
                return false;
            }
        }
        return true;
    }

    /**
     * Compacts raw tag bodies for chip display.
     *
     * @param value raw tag body
     * @return display body
     */
    private static String compactBody(String value) {
        return value.replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Applies rendering hints for crisp rounded chips and text.
     *
     * @param g graphics context
     */
    private static void installQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
    }

    /**
     * Returns the category style.
     *
     * @param category category name
     * @return style
     */
    private static CategoryStyle styleFor(String category) {
        String key = category == null ? "" : category.toUpperCase(Locale.ROOT);
        return switch (key) {
            case "META", "STATUS" -> style(Theme.NN_POLICY, 42, 205);
            case "MOVE", "MATERIAL", "OPENING" -> style(Theme.STATUS_SUCCESS_TEXT, 38, 210);
            case "PIECE", "MOBILITY", "DEVELOPMENT" -> style(Theme.STATUS_WARNING_TEXT, 42, 210);
            case "TACTIC", "THREAT", "CHECK", "ERROR" -> style(Theme.STATUS_ERROR_TEXT, 42, 215);
            case "FACT", "INFO", "SPACE" -> style(Theme.STATUS_INFO_TEXT, 36, 210);
            default -> style(Theme.ACCENT, 34, 210);
        };
    }

    /**
     * Builds a category style from one accent.
     *
     * @param accent accent color
     * @param fillAlpha fill alpha
     * @param borderAlpha border alpha
     * @return category style
     */
    private static CategoryStyle style(Color accent, int fillAlpha, int borderAlpha) {
        Color fill = Theme.withAlpha(accent, Theme.isDark() ? fillAlpha + 10 : fillAlpha);
        Color border = Theme.withAlpha(accent, borderAlpha);
        Color badge = Theme.withAlpha(accent, Theme.isDark() ? 92 : 58);
        return new CategoryStyle(fill, border, accent, badge, Theme.TEXT);
    }

    /**
     * Parsed tag item.
     *
     * @param category category prefix
     * @param display compact display body
     * @param raw original tag text
     */
    private record TagItem(String category, String display, String raw) { }

    /**
     * Category color style.
     *
     * @param fill chip fill
     * @param border chip border
     * @param text heading text
     * @param badge badge fill
     * @param badgeText badge text
     */
    private record CategoryStyle(Color fill, Color border, Color text, Color badge, Color badgeText) { }
}
