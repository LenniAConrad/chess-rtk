package application.gui.workbench.board;

import application.cli.PathOps;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.Toast;
import java.awt.Component;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Save-dialog actions for high quality analysis-board exports.
 *
 * @author Lennart A. Conrad
 */
public final class BoardExportActions {

    /**
     * Timestamp format used in suggested export names.
     */
    private static final DateTimeFormatter STAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Prevents instantiation.
     */
    private BoardExportActions() {
        // utility
    }

    /**
     * Exports a board as a high resolution PNG.
     *
     * @param owner dialog owner
     * @param board source board
     */
    public static void exportPng(Component owner, BoardPanel board) {
        export(owner, board, Format.PNG);
    }

    /**
     * Exports a board as vector SVG.
     *
     * @param owner dialog owner
     * @param board source board
     */
    public static void exportSvg(Component owner, BoardPanel board) {
        export(owner, board, Format.SVG);
    }

    /**
     * Copies a board snapshot to the system clipboard as an image.
     *
     * @param owner toast owner
     * @param board source board
     */
    public static void copyImage(Component owner, BoardPanel board) {
        if (board == null) {
            return;
        }
        try {
            BufferedImage image = BoardExporter.renderPng(board, BoardExporter.DEFAULT_BOARD_SIZE);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new ImageTransferable(image), null);
            toast(owner, Toast.Kind.SUCCESS, "Copied to clipboard");
        } catch (RuntimeException ex) {
            toast(owner, Toast.Kind.ERROR, "Copy failed: " + ex.getMessage());
        }
    }

    /**
     * Copies an SVG representation of the board to the clipboard as text.
     *
     * @param owner toast owner
     * @param board source board
     */
    public static void copySvg(Component owner, BoardPanel board) {
        if (board == null) {
            return;
        }
        try {
            String svg = BoardExporter.toSvg(board, BoardExporter.DEFAULT_BOARD_SIZE);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(svg), null);
            toast(owner, Toast.Kind.SUCCESS, "Copied to clipboard");
        } catch (RuntimeException ex) {
            toast(owner, Toast.Kind.ERROR, "Copy failed: " + ex.getMessage());
        }
    }

    /**
     * Runs one export action.
     *
     * @param owner dialog owner
     * @param board source board
     * @param format output format
     */
    private static void export(Component owner, BoardPanel board, Format format) {
        if (board == null || format == null) {
            return;
        }
        FileNameExtensionFilter filter = new FileNameExtensionFilter(format.description, format.extension);
        JFileChooser chooser = FileDialogs.createFileChooser(format.title,
                PathOps.DEFAULT_DUMP_DIR.resolve(defaultFileName(format.extension)).toFile(), filter);
        int result = chooser.showSaveDialog(owner);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File out = FileDialogs.ensureExtension(chooser.getSelectedFile(),
                "." + format.extension.toLowerCase(Locale.ROOT));
        try {
            if (format == Format.PNG) {
                BoardExporter.writePng(board, out.toPath(), BoardExporter.DEFAULT_BOARD_SIZE);
            } else {
                BoardExporter.writeSvg(board, out.toPath(), BoardExporter.DEFAULT_BOARD_SIZE);
            }
            toast(owner, Toast.Kind.SUCCESS, "Exported " + out.getName());
        } catch (IOException ex) {
            toast(owner, Toast.Kind.ERROR, "Export failed: " + ex.getMessage());
        }
    }

    /**
     * Returns a timestamped default file name.
     *
     * @param extension output extension
     * @return file name
     */
    private static String defaultFileName(String extension) {
        return "analysis-board-" + LocalDateTime.now().format(STAMP_FORMAT) + "." + extension;
    }

    /**
     * Shows a toast when the owner belongs to a workbench frame.
     *
     * @param owner owner component
     * @param kind toast kind
     * @param message toast message
     */
    private static void toast(Component owner, Toast.Kind kind, String message) {
        Window window = owner instanceof Window direct ? direct : SwingUtilities.getWindowAncestor(owner);
        if (window instanceof JFrame frame) {
            Toast.show(frame, kind, message);
        }
    }

    /**
     * Supported board export formats.
     */
    private enum Format {
        /**
         * Portable Network Graphics raster export.
         */
        PNG("Export Analysis Board PNG", "PNG image", "png"),

        /**
         * Scalable Vector Graphics export.
         */
        SVG("Export Analysis Board SVG", "SVG image", "svg");

        /**
         * Dialog title.
         */
        private final String title;

        /**
         * File filter description.
         */
        private final String description;

        /**
         * File extension without leading dot.
         */
        private final String extension;

        /**
         * Creates one format descriptor.
         *
         * @param title dialog title
         * @param description file filter description
         * @param extension file extension
         */
        Format(String title, String description, String extension) {
            this.title = title;
            this.description = description;
            this.extension = extension;
        }
    }

    /**
     * Clipboard wrapper that exposes a rendered board as an image.
     */
    private static final class ImageTransferable implements Transferable {

        /**
         * Image payload.
         */
        private final Image image;

        /**
         * Creates a transferable for one image.
         *
         * @param image image payload
         */
        ImageTransferable(Image image) {
            this.image = image;
        }

        /**
         * Returns the image transfer flavor.
         *
         * @return supported transfer flavors
         */
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.imageFlavor };
        }

        /**
         * Checks whether the requested flavor is the image flavor.
         *
         * @param flavor requested flavor
         * @return true when the flavor is supported
         */
        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        /**
         * Returns the image payload for the supported flavor.
         *
         * @param flavor requested flavor
         * @return image payload
         * @throws UnsupportedFlavorException when the flavor is not an image
         */
        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!DataFlavor.imageFlavor.equals(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
