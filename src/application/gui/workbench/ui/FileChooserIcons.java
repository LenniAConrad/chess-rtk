/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Themed vector icons for Swing file chooser dialogs.
 */
final class FileChooserIcons {

    /**
     * File-list icon size.
     */
    private static final int ITEM_SIZE = 18;

    /**
     * Toolbar icon size.
     */
    private static final int TOOLBAR_SIZE = 20;

    /**
     * Prevents instantiation.
     */
    private FileChooserIcons() {
        // utility
    }

    /**
     * Installs themed icons into Swing defaults used by {@code JFileChooser}.
     */
    static void installDefaults() {
        Icon folder = icon(Kind.FOLDER, ITEM_SIZE);
        Icon file = icon(Kind.FILE, ITEM_SIZE);
        Icon computer = icon(Kind.COMPUTER, ITEM_SIZE);
        Icon drive = icon(Kind.DRIVE, ITEM_SIZE);
        put("FileView.directoryIcon", folder);
        put("FileView.fileIcon", file);
        put("FileView.computerIcon", computer);
        put("FileView.hardDriveIcon", drive);
        put("FileView.floppyDriveIcon", drive);
        put("FileChooser.directoryIcon", folder);
        put("FileChooser.fileIcon", file);
        put("FileChooser.computerIcon", computer);
        put("FileChooser.hardDriveIcon", drive);
        put("FileChooser.floppyDriveIcon", drive);
        put("FileChooser.upFolderIcon", icon(Kind.UP_FOLDER, TOOLBAR_SIZE));
        put("FileChooser.homeFolderIcon", icon(Kind.HOME, TOOLBAR_SIZE));
        put("FileChooser.newFolderIcon", icon(Kind.NEW_FOLDER, TOOLBAR_SIZE));
        put("FileChooser.listViewIcon", icon(Kind.LIST, TOOLBAR_SIZE));
        put("FileChooser.detailsViewIcon", icon(Kind.DETAILS, TOOLBAR_SIZE));
        put("FileChooser.viewMenuIcon", icon(Kind.LIST, TOOLBAR_SIZE));
    }

    /**
     * Stores one icon default.
     *
     * @param key UI defaults key
     * @param value icon value
     */
    private static void put(String key, Icon value) {
        UIManager.put(key, value);
    }

    /**
     * Creates one themed icon.
     *
     * @param kind icon kind
     * @param size icon size
     * @return icon
     */
    private static Icon icon(Kind kind, int size) {
        return new FileChooserIcon(kind, size);
    }

    /**
     * Supported file chooser icon kinds.
     */
    private enum Kind {
        /**
         * Directory list item.
         */
        FOLDER,

        /**
         * File list item.
         */
        FILE,

        /**
         * Parent-directory toolbar action.
         */
        UP_FOLDER,

        /**
         * Home-directory toolbar action.
         */
        HOME,

        /**
         * New-folder toolbar action.
         */
        NEW_FOLDER,

        /**
         * List-view toolbar action.
         */
        LIST,

        /**
         * Details-view toolbar action.
         */
        DETAILS,

        /**
         * Computer/root location.
         */
        COMPUTER,

        /**
         * Drive location.
         */
        DRIVE
    }

    /**
     * Icon implementation that reads colors from the active theme at paint time.
     */
    private static final class FileChooserIcon implements Icon {

        /**
         * Logical drawing viewport.
         */
        private static final double VIEWPORT = 16.0d;

        /**
         * Icon kind.
         */
        private final Kind kind;

        /**
         * Pixel size.
         */
        private final int size;

        /**
         * Creates one icon.
         *
         * @param kind icon kind
         * @param size icon size
         */
        FileChooserIcon(Kind kind, int size) {
            this.kind = kind;
            this.size = size;
        }

        /**
         * Returns icon width.
         *
         * @return width in pixels
         */
        @Override
        public int getIconWidth() {
            return size;
        }

        /**
         * Returns icon height.
         *
         * @return height in pixels
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
                double scale = size / VIEWPORT;
                g.scale(scale, scale);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.setStroke(new BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                paintKind(g);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints the configured icon kind.
         *
         * @param g graphics context
         */
        private void paintKind(Graphics2D g) {
            switch (kind) {
                case FOLDER -> paintFolder(g, false, false);
                case FILE -> paintFile(g);
                case UP_FOLDER -> paintFolder(g, false, true);
                case HOME -> paintHome(g);
                case NEW_FOLDER -> paintFolder(g, true, false);
                case LIST -> paintList(g);
                case DETAILS -> paintDetails(g);
                case COMPUTER -> paintComputer(g);
                case DRIVE -> paintDrive(g);
            }
        }

        /**
         * Paints a folder with optional overlay glyphs.
         *
         * @param g graphics context
         * @param plus true to paint a plus overlay
         * @param up true to paint an up-arrow overlay
         */
        private static void paintFolder(Graphics2D g, boolean plus, boolean up) {
            Path2D tab = new Path2D.Double();
            tab.moveTo(1.7, 4.5);
            tab.lineTo(5.5, 4.5);
            tab.lineTo(6.7, 3.2);
            tab.lineTo(10.0, 3.2);
            tab.lineTo(11.2, 4.5);
            tab.lineTo(14.3, 4.5);
            tab.lineTo(14.3, 6.2);
            tab.lineTo(1.7, 6.2);
            tab.closePath();

            Rectangle2D body = new Rectangle2D.Double(1.4, 5.6, 13.2, 8.5);
            g.setColor(folderTab());
            g.fill(tab);
            g.setColor(folderFill());
            g.fillRoundRect((int) body.getX(), (int) body.getY(),
                    (int) body.getWidth(), (int) body.getHeight(), 2, 2);
            g.setColor(folderLine());
            g.draw(tab);
            g.drawRoundRect((int) body.getX(), (int) body.getY(),
                    (int) body.getWidth(), (int) body.getHeight(), 2, 2);
            if (plus) {
                paintPlus(g, 10.6, 10.0);
            }
            if (up) {
                paintUpArrow(g, 8.5, 9.9);
            }
        }

        /**
         * Paints a file/document icon.
         *
         * @param g graphics context
         */
        private static void paintFile(Graphics2D g) {
            Path2D page = new Path2D.Double();
            page.moveTo(4.0, 1.8);
            page.lineTo(10.4, 1.8);
            page.lineTo(13.0, 4.4);
            page.lineTo(13.0, 14.2);
            page.lineTo(4.0, 14.2);
            page.closePath();
            g.setColor(Theme.ELEVATED_SOLID);
            g.fill(page);
            g.setColor(fileFold());
            Path2D fold = new Path2D.Double();
            fold.moveTo(10.4, 1.9);
            fold.lineTo(10.4, 4.4);
            fold.lineTo(12.9, 4.4);
            fold.closePath();
            g.fill(fold);
            g.setColor(Theme.LINE);
            g.draw(page);
            g.drawLine(10, 2, 13, 5);
            g.setColor(Theme.MUTED);
            g.draw(new Line2D.Double(5.6, 7.3, 11.0, 7.3));
            g.draw(new Line2D.Double(5.6, 9.6, 10.2, 9.6));
            g.draw(new Line2D.Double(5.6, 11.8, 11.4, 11.8));
        }

        /**
         * Paints a home icon.
         *
         * @param g graphics context
         */
        private static void paintHome(Graphics2D g) {
            g.setColor(Theme.withAlpha(Theme.ACCENT, Theme.isDark() ? 70 : 55));
            Path2D roof = new Path2D.Double();
            roof.moveTo(2.0, 8.0);
            roof.lineTo(8.0, 3.0);
            roof.lineTo(14.0, 8.0);
            roof.lineTo(12.6, 9.3);
            roof.lineTo(8.0, 5.4);
            roof.lineTo(3.4, 9.3);
            roof.closePath();
            g.fill(roof);
            g.setColor(Theme.ACCENT);
            g.draw(roof);
            g.setColor(Theme.ELEVATED_SOLID);
            g.fill(new RoundRectangle2D.Double(4.1, 8.2, 7.8, 5.0, 2.0, 2.0));
            g.setColor(Theme.ACCENT);
            g.drawRoundRect(4, 8, 8, 5, 2, 2);
        }

        /**
         * Paints a list-view icon.
         *
         * @param g graphics context
         */
        private static void paintList(Graphics2D g) {
            g.setColor(Theme.MUTED);
            for (int i = 0; i < 3; i++) {
                int y = 4 + i * 4;
                g.drawRoundRect(2, y - 1, 2, 2, 1, 1);
                g.drawLine(6, y, 14, y);
            }
        }

        /**
         * Paints a details-view icon.
         *
         * @param g graphics context
         */
        private static void paintDetails(Graphics2D g) {
            g.setColor(Theme.MUTED);
            for (int row = 0; row < 3; row++) {
                int y = 4 + row * 4;
                g.drawLine(2, y, 14, y);
            }
            g.setColor(Theme.withAlpha(Theme.ACCENT, Theme.isDark() ? 200 : 220));
            g.drawLine(6, 2, 6, 14);
            g.drawLine(10, 2, 10, 14);
        }

        /**
         * Paints a computer icon.
         *
         * @param g graphics context
         */
        private static void paintComputer(Graphics2D g) {
            g.setColor(Theme.withAlpha(Theme.ACCENT, Theme.isDark() ? 58 : 44));
            g.fillRoundRect(2, 3, 12, 8, 2, 2);
            g.setColor(Theme.ACCENT);
            g.drawRoundRect(2, 3, 12, 8, 2, 2);
            g.drawLine(6, 13, 10, 13);
            g.drawLine(8, 11, 8, 13);
        }

        /**
         * Paints a drive icon.
         *
         * @param g graphics context
         */
        private static void paintDrive(Graphics2D g) {
            g.setColor(Theme.ELEVATED_SOLID);
            g.fillRoundRect(2, 5, 12, 7, 2, 2);
            g.setColor(Theme.LINE);
            g.drawRoundRect(2, 5, 12, 7, 2, 2);
            g.setColor(Theme.ACCENT);
            g.fillOval(11, 9, 2, 2);
            g.setColor(Theme.MUTED);
            g.drawLine(4, 8, 9, 8);
        }

        /**
         * Paints a plus overlay.
         *
         * @param g graphics context
         * @param cx center x
         * @param cy center y
         */
        private static void paintPlus(Graphics2D g, double cx, double cy) {
            g.setColor(Theme.TEXT);
            g.drawLine((int) Math.round(cx - 2.2), (int) Math.round(cy),
                    (int) Math.round(cx + 2.2), (int) Math.round(cy));
            g.drawLine((int) Math.round(cx), (int) Math.round(cy - 2.2),
                    (int) Math.round(cx), (int) Math.round(cy + 2.2));
        }

        /**
         * Paints an up arrow overlay.
         *
         * @param g graphics context
         * @param cx center x
         * @param cy center y
         */
        private static void paintUpArrow(Graphics2D g, double cx, double cy) {
            g.setColor(Theme.TEXT);
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(cx, cy - 3.5);
            arrow.lineTo(cx - 3.0, cy - 0.4);
            arrow.moveTo(cx, cy - 3.5);
            arrow.lineTo(cx + 3.0, cy - 0.4);
            arrow.moveTo(cx, cy - 3.3);
            arrow.lineTo(cx, cy + 3.4);
            g.draw(arrow);
        }

        /**
         * Returns folder fill color.
         *
         * @return folder fill
         */
        private static Color folderFill() {
            return Theme.withAlpha(Theme.ACCENT, Theme.isDark() ? 86 : 70);
        }

        /**
         * Returns folder tab color.
         *
         * @return folder tab fill
         */
        private static Color folderTab() {
            return Theme.withAlpha(Theme.ACCENT_HOVER, Theme.isDark() ? 150 : 126);
        }

        /**
         * Returns folder outline color.
         *
         * @return folder outline
         */
        private static Color folderLine() {
            return Theme.isDark() ? Theme.ACCENT_HOVER : Theme.ACCENT_PRESSED;
        }

        /**
         * Returns file folded-corner color.
         *
         * @return fold color
         */
        private static Color fileFold() {
            return Theme.withAlpha(Theme.ACCENT, Theme.isDark() ? 82 : 62);
        }
    }
}
