package application.gui.workbench.session;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Small helper for opening files through AWT desktop integration.
 */
final class DesktopOpen {

    /**
     * Prevents instantiation.
     */
    private DesktopOpen() {
        // utility
    }

    /**
     * Opens a file or directory through the platform desktop shell.
     *
     * @param path path to open
     * @return desktop-open
     */
    static Result open(Path path) {
        if (path == null) {
            return new Result(Status.FAILED, "No path was provided.");
        }
        if (!Desktop.isDesktopSupported()) {
            return new Result(Status.UNSUPPORTED_DESKTOP, "Desktop integration is not supported.");
        }
        Desktop desktop;
        try {
            desktop = Desktop.getDesktop();
        } catch (RuntimeException ex) {
            return new Result(Status.FAILED, ex.getMessage());
        }
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            return new Result(Status.UNSUPPORTED_OPEN, "Opening files is not supported by this desktop.");
        }
        try {
            desktop.open(path.toFile());
            return new Result(Status.OPENED, "");
        } catch (IOException | RuntimeException ex) {
            return new Result(Status.FAILED, ex.getMessage());
        }
    }

    /**
     * Desktop-open status.
     */
    enum Status {
        /**
         * The path was opened.
         */
        OPENED,

        /**
         * AWT desktop integration is unavailable.
         */
        UNSUPPORTED_DESKTOP,

        /**
         * Desktop integration exists but cannot open files.
         */
        UNSUPPORTED_OPEN,

        /**
         * The open action failed.
         */
        FAILED
    }

    /**
     * Result of one desktop-open attempt.
     *
     * @param status status code
     * @param detail human-readable detail
     */
    record Result(Status status, String detail) {
    }
}
