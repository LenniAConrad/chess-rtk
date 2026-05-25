package application.gui.workbench.board;

import application.cli.PathOps;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.Toast;
import java.awt.Component;
import java.awt.Window;
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
}
