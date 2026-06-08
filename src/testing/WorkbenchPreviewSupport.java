package testing;

import application.gui.workbench.ui.AppIcon;
import application.gui.workbench.ui.RenderAcceleration;
import application.gui.workbench.window.Window;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared Workbench preview-window helpers for testing launchers and capture
 * tools.
 */
final class WorkbenchPreviewSupport {

    /**
     * Default preview/capture width.
     */
    static final int DEFAULT_WIDTH = 1500;

    /**
     * Default preview/capture height.
     */
    static final int DEFAULT_HEIGHT = 950;

    /**
     * Deterministic preview window X position.
     */
    private static final int WINDOW_X = 32;

    /**
     * Deterministic preview window Y position.
     */
    private static final int WINDOW_Y = 32;

    /**
     * Prevents instantiation.
     */
    private WorkbenchPreviewSupport() {
        // utility
    }

    /**
     * Opens a Workbench frame on the Swing event dispatch thread.
     *
     * @param fen initial FEN
     * @param whiteDown whether White is shown at the bottom of the board
     * @param target target panel or blank
     * @param width frame width
     * @param height frame height
     * @return visible Workbench frame
     * @throws Exception if the frame cannot be created on the EDT
     */
    static Window openWindowOnEdt(String fen, boolean whiteDown, String target, int width, int height)
            throws Exception {
        return SwingEdt.call(() -> openWindow(fen, whiteDown, target, width, height));
    }

    /**
     * Selects a target panel on the Swing event dispatch thread.
     *
     * @param frame Workbench frame
     * @param target target panel or blank
     * @throws Exception if target selection fails on the EDT
     */
    static void selectTargetOnEdt(Window frame, String target) throws Exception {
        SwingEdt.run(() -> WorkbenchPanelTargets.select(frame, target));
    }

    /**
     * Writes a component dump on the Swing event dispatch thread.
     *
     * @param frame Workbench frame
     * @param target target panel label
     * @param output dump output path
     * @param logPrefix command name for the success log line
     * @throws Exception if the dump cannot be produced or written
     */
    static void writeComponentDump(Window frame, String target, Path output, String logPrefix) throws Exception {
        if (output == null) {
            return;
        }
        String dump = SwingEdt.call(() -> WorkbenchComponentDebug.dump(frame, "live:" + target));
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, dump, StandardCharsets.UTF_8);
        System.out.println(logPrefix + ": wrote component dump " + output.toAbsolutePath());
    }

    /**
     * Opens the Workbench frame at a deterministic size and location.
     *
     * @param fen initial FEN
     * @param whiteDown whether White is shown at the bottom of the board
     * @param target target panel or blank
     * @param width frame width
     * @param height frame height
     * @return visible Workbench frame
     */
    private static Window openWindow(String fen, boolean whiteDown, String target, int width, int height) {
        AppIcon.installDesktopProperties();
        RenderAcceleration.installForWorkbench();
        Window frame = new Window(fen, whiteDown);
        frame.setLocation(WINDOW_X, WINDOW_Y);
        frame.setSize(width, height);
        WorkbenchPanelTargets.select(frame, target);
        frame.toFront();
        return frame;
    }
}
