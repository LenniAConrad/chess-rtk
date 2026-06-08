package testing;

import application.gui.workbench.window.Window;
import chess.core.Setup;
import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Launches the real Swing Workbench on the current display, captures the window
 * with {@link Robot}, writes one PNG, and disposes the frame.
 */
public final class WorkbenchRobotCapture {

    /**
     * Delay after showing the window so first-layout async work can settle.
     */
    private static final int SETTLE_MS = 700;

    /**
     * Prevents instantiation.
     */
    private WorkbenchRobotCapture() {
        // utility
    }

    /**
     * Captures a live Workbench window to a PNG file.
     *
     * @param args optional output path, width, height, initial FEN, target panel,
     *     and component dump path
     * @throws Exception if the window cannot be captured or written
     */
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("WorkbenchRobotCapture needs a display; run under xvfb-run if needed.");
        }
        Path output = Path.of(args.length > 0 && !args[0].isBlank()
                ? args[0] : "artifacts/workbench-live/workbench-preview.png");
        int width = PositiveIntArgs.parse(args, 1, WorkbenchPreviewSupport.DEFAULT_WIDTH);
        int height = PositiveIntArgs.parse(args, 2, WorkbenchPreviewSupport.DEFAULT_HEIGHT);
        String fen = args.length > 3 && !args[3].isBlank()
                ? args[3] : Setup.getStandardStartFEN();
        String target = args.length > 4 ? args[4] : "";
        Path componentDump = args.length > 5 && !args[5].isBlank() ? Path.of(args[5]) : null;

        Files.createDirectories(output.toAbsolutePath().getParent());
        Window frame = WorkbenchPreviewSupport.openWindowOnEdt(fen, true, target, width, height);
        try {
            Robot robot = robot();
            robot.waitForIdle();
            Thread.sleep(SETTLE_MS);
            WorkbenchPreviewSupport.selectTargetOnEdt(frame, target);
            robot.waitForIdle();
            Thread.sleep(SETTLE_MS);
            SwingEdt.run(() -> raiseForCapture(frame));
            robot.waitForIdle();
            Thread.sleep(200L);
            robot.waitForIdle();
            if (componentDump != null) {
                WorkbenchPreviewSupport.writeComponentDump(frame, target, componentDump, "WorkbenchRobotCapture");
            }
            Rectangle bounds = frameBounds(frame);
            BufferedImage image = robot.createScreenCapture(bounds);
            ImageIO.write(image, "png", output.toFile());
            System.out.println("WorkbenchRobotCapture: wrote " + output.toAbsolutePath());
        } finally {
            SwingEdt.run(() -> {
                frame.setAlwaysOnTop(false);
                frame.dispose();
            });
        }
    }

    /**
     * Raises the preview frame above other desktop windows before Robot captures
     * its screen rectangle.
     *
     * @param frame preview frame
     */
    private static void raiseForCapture(Window frame) {
        frame.setVisible(true);
        frame.setAlwaysOnTop(true);
        frame.toFront();
        frame.requestFocus();
        frame.repaint();
    }

    /**
     * Creates the AWT robot with a concise error when the display is unavailable.
     *
     * @return robot for the current display
     */
    private static Robot robot() {
        try {
            return new Robot();
        } catch (AWTException ex) {
            throw new IllegalStateException("Could not create AWT Robot for the current display.", ex);
        }
    }

    /**
     * Returns the screen-space frame bounds.
     *
     * @param frame target frame
     * @return screen bounds
     */
    private static Rectangle frameBounds(Window frame) {
        java.awt.Point location = frame.getLocationOnScreen();
        return new Rectangle(location.x, location.y, frame.getWidth(), frame.getHeight());
    }
}
