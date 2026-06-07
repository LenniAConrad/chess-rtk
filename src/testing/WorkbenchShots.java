package testing;

import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.draw.DrawPanel;
import application.gui.workbench.game.GameModel;
import application.gui.workbench.game.PlayMoveHistoryModel;
import application.gui.workbench.play.PlayPanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JViewport;

/**
 * Visual-debugging utility: renders the main Workbench panels to PNG files in
 * both the light and dark themes, headlessly, so a change can be eyeballed
 * without launching the live application and clicking through tabs.
 *
 * <p>Usage: {@code java -cp out testing.WorkbenchShots [outDir] [width]}
 * (defaults: {@code /tmp/shots} and {@code 1600}). Each panel is written as
 * {@code <name>_<THEME>.png}. A panel that fails to build is skipped with a
 * logged reason so one broken panel never aborts the whole run.</p>
 *
 * <p>The renderer drives Swing layout manually (Swing normally lays out only
 * inside a realized, visible frame), then paints the component tree onto an
 * offscreen image. It covers the panels with self-contained constructors
 * (Dashboard, Datasets, Play); board/Analyze/MCTS tabs that need a live window
 * are out of scope here and are best checked in the running app.</p>
 */
public final class WorkbenchShots {

    /**
     * Number of manual layout passes; a few iterations let width-dependent
     * components (masonry grids, wrapped text) settle.
     */
    private static final int LAYOUT_PASSES = 6;

    private WorkbenchShots() {
    }

    /**
     * Renders every known panel in both themes.
     *
     * @param args optional [outDir] [width]
     * @throws Exception if writing a PNG fails
     */
    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "/tmp/shots";
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 1600;
        new File(outDir).mkdirs();
        Theme.install();
        for (String mode : new String[] {"LIGHT", "DARK"}) {
            Theme.setMode(Theme.Mode.valueOf(mode));
            shoot(outDir, "dashboard", mode, width, 1400, WorkbenchShots::dashboard);
            shoot(outDir, "datasets", mode, width, 1100, WorkbenchShots::datasets);
            shoot(outDir, "datasets-loaded", mode, width, 1100, WorkbenchShots::datasetsLoaded);
            shoot(outDir, "play", mode, Math.max(width, 1500), 900, WorkbenchShots::play);
            shoot(outDir, "draw", mode, Math.max(width, 1500), 900, WorkbenchShots::draw);
            shoot(outDir, "analyze", mode, Math.max(width, 1500), 900, WorkbenchShots::analyze);
            shoot(outDir, "puzzle", mode, Math.max(width, 1500), 1000, WorkbenchShots::puzzle);
            shoot(outDir, "commands", mode, width, 950, WorkbenchShots::commands);
            shoot(outDir, "console", mode, width, 520, WorkbenchShots::console);
            shoot(outDir, "network", mode, Math.max(width, 1600), 1000, WorkbenchShots::network);
            shoot(outDir, "mcts", mode, Math.max(width, 1600), 820, WorkbenchShots::mcts);
            shoot(outDir, "tree", mode, Math.max(width, 1600), 820, WorkbenchShots::tree);
            shoot(outDir, "logs", mode, width, 820, WorkbenchShots::logs);
            shoot(outDir, "publish", mode, Math.max(width, 1500), 950, WorkbenchShots::publish);
        }
        System.out.println("WorkbenchShots: wrote PNGs to " + outDir);
    }

    /**
     * Builds, lays out, and writes one panel to a PNG, skipping on failure.
     *
     * @param outDir output directory
     * @param name file base name
     * @param mode theme name
     * @param width render width
     * @param height render height
     * @param factory panel factory
     */
    private static void shoot(String outDir, String name, String mode, int width, int height,
            Supplier<JComponent> factory) {
        try {
            JComponent panel = factory.get();
            panel.setSize(width, height);
            for (int i = 0; i < LAYOUT_PASSES; i++) {
                layout(panel);
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
        } catch (Exception | Error ex) {
            System.out.println("SKIP " + name + "_" + mode + ": " + ex);
        }
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

    /**
     * Returns a no-op dynamic proxy for an interface, so panels that take a
     * callback interface can be built without a live backend.
     *
     * @param type interface type
     * @return a proxy returning falsy/default values for every method
     */
    private static Object stub(Class<?> type) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, methodArgs) -> {
            Class<?> ret = method.getReturnType();
            if (ret == boolean.class) {
                return false;
            }
            if (ret.isPrimitive() && ret != void.class) {
                return 0;
            }
            return null;
        });
    }

    /**
     * Builds the Dashboard panel with a stub action backend.
     *
     * @return dashboard panel
     */
    private static JComponent dashboard() {
        try {
            Class<?> sessionType = Class.forName("application.gui.workbench.session.Session");
            Object session = sessionType.getDeclaredConstructor().newInstance();
            Class<?> actionsType = Class.forName("application.gui.workbench.dashboard.DashboardActions");
            return (JComponent) Class.forName("application.gui.workbench.dashboard.DashboardPanel")
                    .getDeclaredConstructor(sessionType, actionsType)
                    .newInstance(session, stub(actionsType));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Builds the Datasets panel (empty state).
     *
     * @return datasets panel
     */
    private static JComponent datasets() {
        try {
            return (JComponent) Class.forName("application.gui.workbench.dataset.DatasetPanel")
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Builds the Datasets panel after a scan, to verify the loaded analytics.
     *
     * @return datasets panel with a sample summary applied
     */
    private static JComponent datasetsLoaded() {
        application.gui.workbench.dataset.DatasetPanel panel =
                new application.gui.workbench.dataset.DatasetPanel();
        String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        panel.applySummary(new application.gui.workbench.dataset.DatasetSummary(
                java.nio.file.Path.of("sample.fen"), 1, 2L, 2L, 0L, 0L, 1L, 1L,
                0L, 0L, 0L, 1L, 1L, 0, 8_000, 4_000.0d,
                new int[] {0, 0, 0, 0, 1, 0, 0, 1},
                new int[] {0, 0, 1, 0, 1, 0, 0},
                java.util.List.of(new application.gui.workbench.dataset.DatasetSummary.NamedCount("opening", 1L)),
                java.util.List.of(new application.gui.workbench.dataset.DatasetSummary.NamedCount("stockfish", 1L)),
                java.util.List.of(new application.gui.workbench.dataset.DatasetSummary.SampleRow(
                        "sample.fen", 1L, "FEN", fen, "white", 8_000, "opening", "")),
                java.util.List.of(), false, "Scan complete"));
        return panel;
    }

    /**
     * Builds the Play tab as the live window assembles it: board on the left,
     * the setup form + move list as the right rail.
     *
     * @return play tab component
     */
    private static JComponent play() {
        try {
            String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
            Class<?> hostType = Class.forName("application.gui.workbench.play.PlayHost");
            Class<?> providerType = Class.forName("application.gui.workbench.play.PlaySession$OpponentProvider");
            Class<?> strengthType = Class.forName("application.gui.workbench.play.StrengthModel");
            Object strength = strengthType.getDeclaredConstructor().newInstance();
            Class<?> sessionType = Class.forName("application.gui.workbench.play.PlaySession");
            Object session = sessionType.getDeclaredConstructor(hostType, strengthType, providerType)
                    .newInstance(stub(hostType), strength, stub(providerType));
            Supplier<String> fenSupplier = () -> fen;
            PlayPanel playPanel = (PlayPanel) Class.forName("application.gui.workbench.play.PlayPanel")
                    .getDeclaredConstructor(sessionType, Supplier.class)
                    .newInstance(session, fenSupplier);

            BoardPanel board = new BoardPanel();
            board.setShowNotation(true);
            board.setPositionInstant(new chess.core.Position(fen), chess.core.Move.NO_MOVE);
            JPanel stage = new JPanel(new java.awt.BorderLayout());
            stage.setBackground(Theme.BG);
            stage.add(playPanel.opponentIdentityStrip(), java.awt.BorderLayout.NORTH);
            stage.add(board, java.awt.BorderLayout.CENTER);
            stage.add(playPanel.playerIdentityStrip(), java.awt.BorderLayout.SOUTH);

            GameModel gameModel = new GameModel();
            gameModel.loadLine(new chess.core.Position(fen), java.util.List.of(
                    Short.valueOf(chess.core.Move.parse("e2e4")),
                    Short.valueOf(chess.core.Move.parse("e7e5")),
                    Short.valueOf(chess.core.Move.parse("g1f3")),
                    Short.valueOf(chess.core.Move.parse("b8c6"))));
            JTable moves = new JTable(new PlayMoveHistoryModel(gameModel));
            Theme.table(moves, Theme.TABLE_ROW_HEIGHT);
            JPanel rail = new JPanel(new java.awt.BorderLayout(0, Theme.SPACE_MD));
            rail.setOpaque(false);
            rail.add(playPanel, java.awt.BorderLayout.NORTH);
            JComponent moveHistory = Ui.titled("Move history", new JScrollPane(moves));
            moveHistory.setOpaque(true);
            moveHistory.setBackground(Theme.BG);
            rail.add(moveHistory, java.awt.BorderLayout.CENTER);
            rail.setPreferredSize(new Dimension(400, 560));

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, stage, rail);
            split.setResizeWeight(0.68);
            split.setDividerLocation(0.68);
            return split;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Builds the Draw tab sample: shared board plus annotation/export rail.
     *
     * @return draw tab component
     */
    private static JComponent draw() {
        String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        BoardPanel board = new BoardPanel();
        board.setShowNotation(true);
        board.setPositionInstant(new chess.core.Position(fen), chess.core.Move.NO_MOVE);
        board.setDirectAnnotationMode(true);
        board.addArrow((byte) 12, (byte) 28, new java.awt.Color(0x21, 0x9E, 0x3C, 212), 10);

        JPanel stage = new JPanel(new java.awt.BorderLayout());
        stage.setBackground(Theme.BG);
        stage.add(board, java.awt.BorderLayout.CENTER);

        JScrollPane rail = Ui.scroll(Ui.fillViewport(new DrawPanel(board, stage)));
        rail.setPreferredSize(new Dimension(400, 560));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, stage, rail);
        split.setResizeWeight(0.68);
        split.setDividerLocation(0.68);
        return split;
    }

    /**
     * Builds the Analyze workspace (board + analysis rail) with stub callbacks.
     *
     * @return analyze workspace component
     */
    private static JComponent analyze() {
        try {
            String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
            Class<?> builderType =
                    Class.forName("application.gui.workbench.window.AnalysisWorkspacePanel$CommandBuilder");
            Class<?> type = Class.forName("application.gui.workbench.window.AnalysisWorkspacePanel");
            java.lang.reflect.Constructor<?> ctor = type.getDeclaredConstructor(
                    String.class, boolean.class, builderType,
                    java.util.function.Consumer.class, java.util.function.Consumer.class);
            ctor.setAccessible(true);
            java.util.function.Consumer<Object> noop = value -> { };
            return (JComponent) ctor.newInstance(fen, true, stub(builderType), noop, noop);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Builds the Puzzle trainer without loading the on-disk puzzle library.
     *
     * @return puzzle panel
     */
    private static JComponent puzzle() {
        try {
            java.lang.reflect.Constructor<?> ctor =
                    Class.forName("application.gui.workbench.game.PuzzlePanel")
                            .getDeclaredConstructor(boolean.class);
            ctor.setAccessible(true);
            return (JComponent) ctor.newInstance(false);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Constructs a no-arg panel class reflectively.
     *
     * @param className fully-qualified panel class
     * @return the panel
     */
    private static JComponent simple(String className) {
        try {
            return (JComponent) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Unwraps a controller that exposes a {@code component()} accessor, or
     * returns the object itself when it is already a component.
     *
     * @param panel panel or controller
     * @return the renderable component
     */
    private static JComponent asComponent(Object panel) {
        if (panel instanceof JComponent component) {
            return component;
        }
        try {
            return (JComponent) panel.getClass().getMethod("component").invoke(panel);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * @return the Commands tab builder form.
     */
    private static JComponent commands() {
        return simple("application.gui.workbench.command.CommandForm");
    }

    /**
     * @return the Network inspector tab.
     */
    private static JComponent network() {
        return simple("application.gui.workbench.network.NetworkPanel");
    }

    /**
     * @return the Logs tab.
     */
    private static JComponent logs() {
        return simple("application.gui.workbench.session.LogPanel");
    }

    /**
     * @return the Console with a few sample lines.
     */
    private static JComponent console() {
        try {
            Class<?> type = Class.forName("application.gui.workbench.command.Console");
            Object console = type.getDeclaredConstructor().newInstance();
            type.getMethod("applyConsoleTheme").invoke(console);
            java.lang.reflect.Method append = type.getMethod("appendOutput", String.class);
            append.invoke(console, "$ crtk engine bestmove --fen startpos --depth 18\n");
            append.invoke(console, "info depth 18 score cp 24 pv e2e4 e7e5 g1f3\nbestmove e2e4\n");
            append.invoke(console, "[exit 0] · engine bestmove · 1.24s\n");
            return (JComponent) console;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * @return the MCTS inspector tab.
     */
    private static JComponent mcts() {
        try {
            Class<?> sessionType = Class.forName("application.gui.workbench.mcts.MctsSession");
            Object session = sessionType.getDeclaredConstructor().newInstance();
            Supplier<String> fen = () -> "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
            return (JComponent) Class.forName("application.gui.workbench.mcts.MctsPanel")
                    .getDeclaredConstructor(sessionType, Supplier.class)
                    .newInstance(session, fen);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * @return the Engine "Tree" tab (boxed PUCT controls, growth scrubber,
     *     collapsible inspector) over an empty shared session.
     */
    private static JComponent tree() {
        try {
            Class<?> sessionType = Class.forName("application.gui.workbench.mcts.MctsSession");
            Object session = sessionType.getDeclaredConstructor().newInstance();
            Supplier<String> fen = () -> "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
            return (JComponent) Class.forName("application.gui.workbench.mcts.TreePanel")
                    .getDeclaredConstructor(sessionType, Supplier.class)
                    .newInstance(session, fen);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * @return the Publish tab.
     */
    private static JComponent publish() {
        try {
            Class<?> hostType = Class.forName("application.gui.workbench.publish.PublishingPanel$Host");
            Object host = richStub(hostType, java.util.Map.of(
                    "currentFen", () -> "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    "reportPanel", JPanel::new));
            Object panel = Class.forName("application.gui.workbench.publish.PublishingPanel")
                    .getDeclaredConstructor(hostType).newInstance(host);
            return asComponent(panel);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Returns a dynamic proxy with per-method overrides, falling back to sensible
     * defaults (empty string / list, a placeholder panel, falsy primitives).
     *
     * @param type interface type
     * @param overrides method-name to value supplier
     * @return the proxy
     */
    private static Object richStub(Class<?> type, java.util.Map<String, Supplier<Object>> overrides) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Supplier<Object> override = overrides.get(method.getName());
            if (override != null) {
                return override.get();
            }
            Class<?> ret = method.getReturnType();
            if (ret == boolean.class) {
                return false;
            }
            if (ret == String.class) {
                return "";
            }
            if (javax.swing.JComponent.class.isAssignableFrom(ret)) {
                return new JPanel();
            }
            if (java.util.List.class.isAssignableFrom(ret)) {
                return java.util.List.of();
            }
            if (ret.isPrimitive() && ret != void.class) {
                return 0;
            }
            return null;
        });
    }
}
