package testing;

import application.gui.workbench.window.Window;
import chess.core.Setup;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

/**
 * Launches the real Swing Workbench for preview automation, optionally selecting
 * one debug target and writing a component-tree dump.
 */
public final class WorkbenchPreviewLauncher {

    /**
     * Time to let lazy panels and first-layout work settle before dumping.
     */
    private static final int SETTLE_MS = 900;

    /**
     * Prevents instantiation.
     */
    private WorkbenchPreviewLauncher() {
        // utility
    }

    /**
     * Launches the Workbench and leaves it running.
     *
     * @param args optional FEN, whiteDown, target, component dump path, width, height
     * @throws Exception if the window cannot be created or dumped
     */
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("WorkbenchPreviewLauncher needs a display.");
        }
        String fen = args.length > 0 && !args[0].isBlank() ? args[0] : Setup.getStandardStartFEN();
        boolean whiteDown = args.length <= 1 || args[1].isBlank() || Boolean.parseBoolean(args[1]);
        String target = args.length > 2 ? args[2] : "";
        Path componentDump = args.length > 3 && !args[3].isBlank() ? Path.of(args[3]) : null;
        int width = PositiveIntArgs.parse(args, 4, WorkbenchPreviewSupport.DEFAULT_WIDTH);
        int height = PositiveIntArgs.parse(args, 5, WorkbenchPreviewSupport.DEFAULT_HEIGHT);

        Window frame = WorkbenchPreviewSupport.openWindowOnEdt(fen, whiteDown, target, width, height);
        if (componentDump != null) {
            Thread.sleep(SETTLE_MS);
            WorkbenchPreviewSupport.selectTargetOnEdt(frame, target);
            Thread.sleep(SETTLE_MS);
            WorkbenchPreviewSupport.writeComponentDump(frame, target, componentDump, "WorkbenchPreviewLauncher");
        }
        System.out.println("WorkbenchPreviewLauncher: ready target="
                + (target == null || target.isBlank() ? "default" : target));
    }
}
