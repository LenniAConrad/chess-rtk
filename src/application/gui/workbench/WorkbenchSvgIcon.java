package application.gui.workbench;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractButton;
import javax.swing.Icon;

/**
 * Small native Java2D icons backed by embedded SVG-style vector geometry.
 */
final class WorkbenchSvgIcon implements Icon {

    /**
     * Diagnostic logger for unknown button labels.
     */
    private static final Logger LOGGER = Logger.getLogger(WorkbenchSvgIcon.class.getName());

    /**
     * Icon artwork size before scaling.
     */
    private static final int VIEWBOX = 24;

    /**
     * Cached stroke for fine outline icons.
     */
    private static final BasicStroke STROKE_FINE = new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    /**
     * Cached stroke for medium outline icons.
     */
    private static final BasicStroke STROKE_MEDIUM = new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    /**
     * Cached stroke for thick outline icons.
     */
    private static final BasicStroke STROKE_THICK = new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    /**
     * Cached stroke for solid plus/back glyphs.
     */
    private static final BasicStroke STROKE_BOLD = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    /**
     * Cached play triangle.
     */
    private static final Path2D PLAY_PATH = makePlayPath();

    /**
     * Cached reset arc.
     */
    private static final Path2D RESET_ARC = makeResetArc();

    /**
     * Cached reset arrowhead.
     */
    private static final Path2D RESET_HEAD = makeResetHead();

    /**
     * Cached tag outline.
     */
    private static final Path2D TAG_PATH = makeTagPath();

    /**
     * Cached back-arrow head.
     */
    private static final Path2D BACK_HEAD = makeBackHead();

    /**
     * Cached file-page outline.
     */
    private static final Path2D FILE_PAGE = makeFilePage();

    /**
     * Icon kind.
     */
    private final Kind kind;

    /**
     * Display size in pixels.
     */
    private final int size;

    /**
     * Icon color.
     */
    private final Color color;

    /**
     * Creates an icon.
     *
     * @param kind icon kind
     * @param size display size
     * @param color icon color
     */
    WorkbenchSvgIcon(Kind kind, int size, Color color) {
        this.kind = kind;
        this.size = size;
        this.color = color;
    }

    /**
     * Returns a best-fit icon for a button.
     *
     * @param button source button
     * @param primary whether this is a primary action
     * @return icon, or null when no icon applies
     */
    static Icon forButton(AbstractButton button, boolean primary) {
        Color iconColor = primary ? WorkbenchTheme.PRIMARY_BUTTON_TEXT : WorkbenchTheme.SECONDARY_BUTTON_TEXT;
        return iconForButton(button, iconColor);
    }

    /**
     * Returns a muted icon for a disabled button.
     *
     * @param button source button
     * @return icon, or null when no icon applies
     */
    static Icon disabledForButton(AbstractButton button) {
        return iconForButton(button, WorkbenchTheme.BUTTON_DISABLED_TEXT);
    }

    /**
     * Returns a best-fit icon honoring the optional icon-kind client property,
     * falling back to a label-based mapping.
     *
     * @param button source button
     * @param iconColor icon color
     * @return icon, or null when no icon applies
     */
    private static Icon iconForButton(AbstractButton button, Color iconColor) {
        Object explicit = button.getClientProperty(WorkbenchTheme.CLIENT_ICON_KIND);
        if (explicit instanceof Kind kind) {
            return new WorkbenchSvgIcon(kind, 16, iconColor);
        }
        Kind kind = kindForLabel(button.getText());
        if (kind == null) {
            return null;
        }
        return new WorkbenchSvgIcon(kind, 16, iconColor);
    }

    /**
     * Maps a label to its icon kind, logging unknown labels for diagnostics.
     *
     * @param text button label
     * @return icon kind, or null when no icon should be shown
     */
    private static Kind kindForLabel(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Kind kind = switch (text) {
            case "Load", "Load Line", "Run", "Run Batch", "Run Publishing", "Generate Report", "Search", "Best",
                    "Analyze", "Forward", "End", "Smoke" ->
                    Kind.PLAY;
            case "Reset", "Drop Optional", "Clear", "Defaults" -> Kind.RESET;
            case "Flip" -> Kind.FLIP;
            case "Copy", "Copy FEN", "Copy Command", "Copy Report", "Copy PGN", "Copy SAN", "Copy UCI",
                    "Copy FEN List" ->
                    Kind.COPY;
            case "Stop" -> Kind.STOP;
            case "Info", "Info +", "Info -" -> Kind.INFO;
            case "Settings" -> Kind.SETTINGS;
            case "Tags" -> Kind.TAG;
            case "Actions", "Commands", "Batch", "Game", "Perft", "Engine", "Validate Config" -> Kind.GRID;
            case "Add Current FEN", "Add to Batch", "New Game" -> Kind.PLUS;
            case "Back", "Start" -> Kind.BACK;
            case "Publish", "Load File", "Save PGN", "Save Report", "Choose Input", "Choose Output", "Choose PDF",
                    "Choose Cover", "Choose Protocol" ->
                    Kind.FILE;
            default -> null;
        };
        if (kind == null) {
            LOGGER.log(Level.FINE, "No workbench icon for button label \"{0}\"; set CLIENT_ICON_KIND to override.",
                    text);
        }
        return kind;
    }

    /**
     * Returns icon width.
     *
     * @return width
     */
    @Override
    public int getIconWidth() {
        return size;
    }

    /**
     * Returns icon height.
     *
     * @return height
     */
    @Override
    public int getIconHeight() {
        return size;
    }

    /**
     * Paints the icon.
     *
     * @param component target component
     * @param graphics graphics context
     * @param x x coordinate
     * @param y y coordinate
     */
    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(x, y);
            double scale = size / (double) VIEWBOX;
            g.scale(scale, scale);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintKind(g);
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the selected icon kind.
     *
     * @param g graphics context
     */
    private void paintKind(Graphics2D g) {
        g.setColor(color);
        switch (kind) {
            case LOGO -> paintLogo(g);
            case PLAY -> paintPlay(g);
            case COPY -> paintCopy(g);
            case RESET -> paintReset(g);
            case STOP -> paintStop(g);
            case INFO -> paintInfo(g);
            case SETTINGS -> paintSettings(g);
            case FLIP -> paintFlip(g);
            case TAG -> paintTag(g);
            case GRID -> paintGrid(g);
            case PLUS -> paintPlus(g);
            case BACK -> paintBack(g);
            case FILE -> paintFile(g);
        }
    }

    /**
     * Paints a compact CRTK-style segmented mark based on the repo SVG.
     *
     * @param g graphics context
     */
    private void paintLogo(Graphics2D g) {
        g.setColor(WorkbenchTheme.LOGO_BACKGROUND);
        g.fillRoundRect(1, 1, 22, 22, 5, 5);
        g.setColor(WorkbenchTheme.LOGO_MARK);
        g.fill(new Rectangle2D.Double(5, 6, 4, 2));
        g.fill(new Rectangle2D.Double(5, 10, 4, 2));
        g.fill(new Rectangle2D.Double(5, 14, 2, 5));
        g.fill(new Rectangle2D.Double(11, 6, 6, 2));
        g.fill(new Rectangle2D.Double(13, 8, 2, 11));
        g.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(18, 7, 14, 12);
        g.drawLine(15, 12, 19, 18);
    }

    /**
     * Paints a compact settings gear.
     *
     * @param g graphics context
     */
    private void paintSettings(Graphics2D g) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int center = 12;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            int innerX = center + (int) Math.round(Math.cos(angle) * 7.0);
            int innerY = center + (int) Math.round(Math.sin(angle) * 7.0);
            int outerX = center + (int) Math.round(Math.cos(angle) * 9.0);
            int outerY = center + (int) Math.round(Math.sin(angle) * 9.0);
            g.drawLine(innerX, innerY, outerX, outerY);
        }
        g.drawOval(6, 6, 12, 12);
        g.fillOval(10, 10, 4, 4);
    }

    /**
     * Paints a play triangle.
     *
     * @param g graphics context
     */
    private void paintPlay(Graphics2D g) {
        g.fill(PLAY_PATH);
    }

    /**
     * Paints a copy icon.
     *
     * @param g graphics context
     */
    private void paintCopy(Graphics2D g) {
        g.setStroke(STROKE_FINE);
        g.drawRoundRect(8, 5, 9, 11, 2, 2);
        g.drawRoundRect(5, 8, 9, 11, 2, 2);
    }

    /**
     * Paints a reset icon.
     *
     * @param g graphics context
     */
    private void paintReset(Graphics2D g) {
        g.setStroke(STROKE_THICK);
        g.draw(RESET_ARC);
        g.fill(RESET_HEAD);
    }

    /**
     * Paints a stop icon.
     *
     * @param g graphics context
     */
    private void paintStop(Graphics2D g) {
        g.fillRoundRect(7, 7, 10, 10, 2, 2);
    }

    /**
     * Paints an information icon.
     *
     * @param g graphics context
     */
    private void paintInfo(Graphics2D g) {
        g.setStroke(STROKE_FINE);
        g.drawOval(5, 5, 14, 14);
        g.fillOval(11, 8, 2, 2);
        g.drawLine(12, 12, 12, 16);
    }

    /**
     * Paints a flip icon.
     *
     * @param g graphics context
     */
    private void paintFlip(Graphics2D g) {
        g.setStroke(STROKE_MEDIUM);
        g.drawArc(4, 5, 13, 9, 20, 190);
        g.drawArc(7, 10, 13, 9, 200, 190);
        g.fillPolygon(FLIP_HEAD_TOP_X, FLIP_HEAD_TOP_Y, 3);
        g.fillPolygon(FLIP_HEAD_BOTTOM_X, FLIP_HEAD_BOTTOM_Y, 3);
    }

    /**
     * Paints a tag icon.
     *
     * @param g graphics context
     */
    private void paintTag(Graphics2D g) {
        g.setStroke(STROKE_FINE);
        g.draw(TAG_PATH);
        g.fillOval(8, 8, 2, 2);
    }

    /**
     * Paints a grid icon.
     *
     * @param g graphics context
     */
    private void paintGrid(Graphics2D g) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                g.fillRoundRect(5 + col * 5, 5 + row * 5, 3, 3, 1, 1);
            }
        }
    }

    /**
     * Paints a plus icon.
     *
     * @param g graphics context
     */
    private void paintPlus(Graphics2D g) {
        g.setStroke(STROKE_BOLD);
        g.drawLine(12, 6, 12, 18);
        g.drawLine(6, 12, 18, 12);
    }

    /**
     * Paints a back arrow.
     *
     * @param g graphics context
     */
    private void paintBack(Graphics2D g) {
        g.setStroke(STROKE_BOLD);
        g.drawLine(8, 12, 18, 12);
        g.fill(BACK_HEAD);
    }

    /**
     * Paints a file icon.
     *
     * @param g graphics context
     */
    private void paintFile(Graphics2D g) {
        g.setStroke(STROKE_FINE);
        g.draw(FILE_PAGE);
        g.drawLine(14, 4, 14, 8);
        g.drawLine(14, 8, 18, 8);
        g.drawLine(10, 12, 15, 12);
        g.drawLine(10, 15, 15, 15);
    }

    /**
     * Cached flip-arrow top head x coordinates.
     */
    private static final int[] FLIP_HEAD_TOP_X = { 16, 20, 17 };

    /**
     * Cached flip-arrow top head y coordinates.
     */
    private static final int[] FLIP_HEAD_TOP_Y = { 5, 7, 10 };

    /**
     * Cached flip-arrow bottom head x coordinates.
     */
    private static final int[] FLIP_HEAD_BOTTOM_X = { 8, 4, 7 };

    /**
     * Cached flip-arrow bottom head y coordinates.
     */
    private static final int[] FLIP_HEAD_BOTTOM_Y = { 19, 17, 14 };

    /**
     * Builds the cached play-triangle path.
     *
     * @return cached path
     */
    private static Path2D makePlayPath() {
        Path2D path = new Path2D.Double();
        path.moveTo(8, 5.7);
        path.lineTo(18.4, 12);
        path.lineTo(8, 18.3);
        path.closePath();
        return path;
    }

    /**
     * Builds the cached reset arc.
     *
     * @return cached arc
     */
    private static Path2D makeResetArc() {
        Path2D arc = new Path2D.Double();
        arc.moveTo(17.4, 8.2);
        arc.curveTo(15.8, 5.8, 12.8, 4.8, 10.0, 5.6);
        arc.curveTo(6.3, 6.7, 4.3, 10.5, 5.4, 14.1);
        arc.curveTo(6.6, 17.9, 10.5, 20.0, 14.1, 18.7);
        arc.curveTo(16.0, 18.0, 17.5, 16.7, 18.3, 15.0);
        return arc;
    }

    /**
     * Builds the cached reset arrowhead.
     *
     * @return cached arrowhead
     */
    private static Path2D makeResetHead() {
        Path2D head = new Path2D.Double();
        head.moveTo(17.4, 4.5);
        head.lineTo(18.8, 9.1);
        head.lineTo(14.1, 8.1);
        head.closePath();
        return head;
    }

    /**
     * Builds the cached tag-outline path.
     *
     * @return cached path
     */
    private static Path2D makeTagPath() {
        Path2D path = new Path2D.Double();
        path.moveTo(5, 6);
        path.lineTo(13, 6);
        path.lineTo(19, 12);
        path.lineTo(12, 19);
        path.lineTo(5, 12);
        path.closePath();
        return path;
    }

    /**
     * Builds the cached back-arrow head.
     *
     * @return cached head
     */
    private static Path2D makeBackHead() {
        Path2D head = new Path2D.Double();
        head.moveTo(8, 12);
        head.lineTo(13, 7);
        head.lineTo(13, 17);
        head.closePath();
        return head;
    }

    /**
     * Builds the cached file-page outline.
     *
     * @return cached page
     */
    private static Path2D makeFilePage() {
        Path2D page = new Path2D.Double();
        page.moveTo(7, 4);
        page.lineTo(14, 4);
        page.lineTo(18, 8);
        page.lineTo(18, 20);
        page.lineTo(7, 20);
        page.closePath();
        return page;
    }

    /**
     * Available embedded vector icons.
     */
    enum Kind {
        /**
         * CRTK segmented logo mark.
         */
        LOGO,

        /**
         * Play or run command.
         */
        PLAY,

        /**
         * Copy command.
         */
        COPY,

        /**
         * Reset command.
         */
        RESET,

        /**
         * Stop command.
         */
        STOP,

        /**
         * Information toggle.
         */
        INFO,

        /**
         * Settings command.
         */
        SETTINGS,

        /**
         * Board flip command.
         */
        FLIP,

        /**
         * Tag command.
         */
        TAG,

        /**
         * Grid or perft command.
         */
        GRID,

        /**
         * Add command.
         */
        PLUS,

        /**
         * Back or start navigation.
         */
        BACK,

        /**
         * File import/export command.
         */
        FILE
    }
}
