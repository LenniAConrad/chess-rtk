package testing;

import application.gui.workbench.ui.Theme;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JViewport;

/**
 * Visual-debugging utility: renders the main Workbench panels to PNG files in
 * both the light and dark themes, headlessly, so a change can be eyeballed
 * without launching the live application and clicking through tabs.
 *
 * <p>Usage: {@code java -cp out testing.WorkbenchShots [outDir] [width]
 * [panelsCsv] [themesCsv] [componentDumpDir]} (defaults: {@code /tmp/shots},
 * {@code 1600}, {@code all}, {@code LIGHT,DARK}, and no component dump).
 * Each panel is written as {@code <name>_<THEME>.png}. When a component dump
 * directory is supplied, {@code <name>_<THEME>.components.txt} is written
 * beside the rendered panel diagnostics. A panel that fails to build is
 * skipped with a logged reason so one broken panel never aborts the whole
 * run.</p>
 *
 * <p>The renderer drives Swing layout manually (Swing normally lays out only
 * inside a realized, visible frame), then paints the component tree onto an
 * offscreen image. It covers panels with self-contained constructors; full
 * shell behavior, top-level tab targeting, and lazy live-window materialization
 * are checked through {@link WorkbenchRobotCapture} and
 * {@link WorkbenchPreviewLauncher}.</p>
 */
public final class WorkbenchShots {

    /**
     * Default screenshot width.
     */
    private static final int DEFAULT_WIDTH = 1600;

    /**
     * Number of manual layout passes; a few iterations let width-dependent
     * components (masonry grids, wrapped text) settle.
     */
    private static final int LAYOUT_PASSES = 6;

    /**
     * Prevents instantiation.
     */
    private WorkbenchShots() {
        // utility
    }

    /**
     * Renders every known panel in both themes.
     *
     * @param args optional [outDir] [width] [panelsCsv] [themesCsv] [componentDumpDir]
     * @throws Exception if writing a PNG fails
     */
    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "/tmp/shots";
        int width = PositiveIntArgs.parse(args, 1, DEFAULT_WIDTH);
        Set<String> panels = csv(args.length > 2 ? args[2] : "all");
        Set<String> themes = csv(args.length > 3 ? args[3] : "LIGHT,DARK");
        Path componentDumpDir = args.length > 4 && !args[4].isBlank() ? Path.of(args[4]) : null;
        new File(outDir).mkdirs();
        Theme.install();
        for (String mode : new String[] {"LIGHT", "DARK"}) {
            if (!enabled(themes, mode)) {
                continue;
            }
            Theme.setMode(Theme.Mode.valueOf(mode));
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "dashboard", mode, width, 1400, WorkbenchShotPanels::dashboard);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "datasets", mode, width, 1100, WorkbenchShotPanels::datasets);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "datasets-loaded", mode, width, 1100, WorkbenchShotPanels::datasetsLoaded);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "play", mode, Math.max(width, 1500), 900, WorkbenchShotPanels::play);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "draw", mode, Math.max(width, 1500), 900, WorkbenchShotPanels::draw);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "analyze", mode, Math.max(width, 1500), 900, WorkbenchShotPanels::analyze);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "puzzle", mode, Math.max(width, 1500), 1000, WorkbenchShotPanels::puzzle);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "commands", mode, width, 950, WorkbenchShotPanels::commands);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "console", mode, width, 520, WorkbenchShotPanels::console);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "network", mode, Math.max(width, 1600), 1000, WorkbenchShotPanels::network);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "mcts", mode, Math.max(width, 1600), 820, WorkbenchShotPanels::mcts);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "tree", mode, Math.max(width, 1600), 820, WorkbenchShotPanels::tree);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "logs", mode, width, 820, WorkbenchShotPanels::logs);
            shootIfEnabled(panels, outDir, componentDumpDir,
                    "publish", mode, Math.max(width, 1500), 950, WorkbenchShotPanels::publish);
        }
        System.out.println("WorkbenchShots: wrote PNGs to " + outDir);
    }

    /**
     * Renders one panel only when it matches the selected set.
     *
     * @param panels selected panel names
     * @param outDir output directory
     * @param componentDumpDir optional component dump output directory
     * @param name file base name
     * @param mode theme name
     * @param width render width
     * @param height render height
     * @param factory panel factory
     */
    private static void shootIfEnabled(Set<String> panels, String outDir, Path componentDumpDir,
            String name, String mode, int width, int height, Supplier<JComponent> factory) {
        if (!enabled(panels, name)) {
            return;
        }
        shoot(outDir, componentDumpDir, name, mode, width, height, factory);
    }

    /**
     * Builds, lays out, and writes one panel to a PNG, skipping on failure.
     *
     * @param outDir output directory
     * @param componentDumpDir optional component dump output directory
     * @param name file base name
     * @param mode theme name
     * @param width render width
     * @param height render height
     * @param factory panel factory
     */
    private static void shoot(String outDir, Path componentDumpDir, String name, String mode, int width, int height,
            Supplier<JComponent> factory) {
        try {
            System.out.println("START " + name + "_" + mode);
            System.out.flush();
            SwingEdt.run(() -> {
                JComponent panel = factory.get();
                panel.setSize(width, height);
                for (int i = 0; i < LAYOUT_PASSES; i++) {
                    layout(panel);
                }
                if (componentDumpDir != null) {
                    Path dump = componentDumpDir.resolve(name + "_" + mode + ".components.txt");
                    WorkbenchComponentDebug.write(panel, name + "_" + mode, dump);
                    System.out.println("DUMP " + dump);
                }
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                g.setColor(Theme.BG);
                g.fillRect(0, 0, width, height);
                panel.paint(g);
                g.dispose();
                File out = new File(outDir, name + "_" + mode + ".png");
                ImageIO.write(image, "png", out);
                System.out.println("OK " + out);
            });
        } catch (Exception | Error ex) {
            System.out.println("SKIP " + name + "_" + mode + ": " + ex);
        }
    }

    /**
     * Parses a comma-separated selector list.
     *
     * @param value raw selector
     * @return normalized selector set
     */
    private static Set<String> csv(String value) {
        Set<String> out = new LinkedHashSet<>();
        for (String token : (value == null ? "" : value).split(",")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        if (out.isEmpty()) {
            out.add("all");
        }
        return out;
    }

    /**
     * Returns whether a selector set enables a name.
     *
     * @param selected selected names or all
     * @param name candidate name
     * @return true when enabled
     */
    private static boolean enabled(Set<String> selected, String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return selected.contains("all") || selected.contains(normalized);
    }

    /**
     * Recursively forces a Swing layout pass over a component tree, sizing
     * scroll-pane views to their preferred height so scrolled content renders
     * in full.
     *
     * @param component subtree root
     */
    private static void layout(Component component) {
        if (component instanceof JViewport viewport) {
            Component view = viewport.getView();
            if (view != null) {
                Dimension preferred = view.getPreferredSize();
                view.setBounds(0, 0, viewport.getWidth(),
                        Math.max(preferred.height, viewport.getHeight()));
            }
        }
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layout(child);
            }
        }
    }

}
