package testing;

import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.draw.DrawPanel;
import application.gui.workbench.game.GameModel;
import application.gui.workbench.game.PlayMoveHistoryModel;
import application.gui.workbench.play.PlayPanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Color;
import java.awt.Dimension;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;

/**
 * Builds sample Workbench panels for {@link WorkbenchShots}.
 */
final class WorkbenchShotPanels {

    /**
     * Standard starting position used by screenshot fixtures.
     */
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Preferred right-rail width for board-based screenshot fixtures.
     */
    private static final int RAIL_WIDTH = 400;

    /**
     * Preferred right-rail height for board-based screenshot fixtures.
     */
    private static final int RAIL_HEIGHT = 560;

    /**
     * Board-to-rail split weight used by board-based screenshot fixtures.
     */
    private static final double BOARD_SPLIT_WEIGHT = 0.68d;

    /**
     * Sample arrow color used by the Draw screenshot fixture.
     */
    private static final Color SAMPLE_ARROW_COLOR = new Color(0x21, 0x9E, 0x3C, 212);

    /**
     * Sample arrow stroke width used by the Draw screenshot fixture.
     */
    private static final int SAMPLE_ARROW_WIDTH = 10;

    /**
     * Prevents instantiation.
     */
    private WorkbenchShotPanels() {
        // utility
    }

    /**
     * Builds the Dashboard panel with a stub action backend.
     *
     * @return dashboard panel
     */
    static JComponent dashboard() {
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
     * Builds the Datasets panel in its empty state.
     *
     * @return datasets panel
     */
    static JComponent datasets() {
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
    static JComponent datasetsLoaded() {
        application.gui.workbench.dataset.DatasetPanel panel =
                new application.gui.workbench.dataset.DatasetPanel();
        panel.applySummary(new application.gui.workbench.dataset.DatasetSummary(
                java.nio.file.Path.of("sample.fen"), 1, 2L, 2L, 0L, 0L, 1L, 1L,
                0L, 0L, 0L, 1L, 1L, 0, 8_000, 4_000.0d,
                new int[] {0, 0, 0, 0, 1, 0, 0, 1},
                new int[] {0, 0, 1, 0, 1, 0, 0},
                List.of(new application.gui.workbench.dataset.DatasetSummary.NamedCount("opening", 1L)),
                List.of(new application.gui.workbench.dataset.DatasetSummary.NamedCount("stockfish", 1L)),
                List.of(new application.gui.workbench.dataset.DatasetSummary.SampleRow(
                        "sample.fen", 1L, "FEN", START_FEN, "white", 8_000, "opening", "")),
                List.of(), false, "Scan complete"));
        return panel;
    }

    /**
     * Builds the Play tab as the live window assembles it: board on the left,
     * the setup form and move list as the right rail.
     *
     * @return play tab component
     */
    static JComponent play() {
        try {
            Class<?> hostType = Class.forName("application.gui.workbench.play.PlayHost");
            Class<?> providerType = Class.forName("application.gui.workbench.play.PlaySession$OpponentProvider");
            Class<?> strengthType = Class.forName("application.gui.workbench.play.StrengthModel");
            Object strength = strengthType.getDeclaredConstructor().newInstance();
            Class<?> sessionType = Class.forName("application.gui.workbench.play.PlaySession");
            Object session = sessionType.getDeclaredConstructor(hostType, strengthType, providerType)
                    .newInstance(stub(hostType), strength, stub(providerType));
            Supplier<String> fenSupplier = () -> START_FEN;
            PlayPanel playPanel = (PlayPanel) Class.forName("application.gui.workbench.play.PlayPanel")
                    .getDeclaredConstructor(sessionType, Supplier.class)
                    .newInstance(session, fenSupplier);

            BoardPanel board = new BoardPanel();
            board.setShowNotation(true);
            board.setPositionInstant(new chess.core.Position(START_FEN), chess.core.Move.NO_MOVE);
            JPanel stage = new JPanel(new java.awt.BorderLayout());
            stage.setBackground(Theme.BG);
            stage.add(playPanel.opponentIdentityStrip(), java.awt.BorderLayout.NORTH);
            stage.add(board, java.awt.BorderLayout.CENTER);
            stage.add(playPanel.playerIdentityStrip(), java.awt.BorderLayout.SOUTH);

            GameModel gameModel = new GameModel();
            gameModel.loadLine(new chess.core.Position(START_FEN), List.of(
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
            rail.setPreferredSize(new Dimension(RAIL_WIDTH, RAIL_HEIGHT));

            return boardSplit(stage, rail);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Builds the Draw tab sample: shared board plus annotation/export rail.
     *
     * @return draw tab component
     */
    static JComponent draw() {
        BoardPanel board = new BoardPanel();
        board.setShowNotation(true);
        board.setPositionInstant(new chess.core.Position(START_FEN), chess.core.Move.NO_MOVE);
        board.setDirectAnnotationMode(true);
        board.addArrow((byte) 12, (byte) 28, SAMPLE_ARROW_COLOR, SAMPLE_ARROW_WIDTH);

        JPanel stage = new JPanel(new java.awt.BorderLayout());
        stage.setBackground(Theme.BG);
        stage.add(board, java.awt.BorderLayout.CENTER);

        JScrollPane rail = Ui.scroll(Ui.fillViewport(new DrawPanel(board, stage)));
        rail.setPreferredSize(new Dimension(RAIL_WIDTH, RAIL_HEIGHT));
        return boardSplit(stage, rail);
    }

    /**
     * Builds the Analyze workspace with stub callbacks.
     *
     * @return analyze workspace component
     */
    static JComponent analyze() {
        try {
            Class<?> builderType =
                    Class.forName("application.gui.workbench.window.AnalysisWorkspacePanel$CommandBuilder");
            Class<?> type = Class.forName("application.gui.workbench.window.AnalysisWorkspacePanel");
            java.lang.reflect.Constructor<?> ctor = type.getDeclaredConstructor(
                    String.class, boolean.class, builderType,
                    java.util.function.Consumer.class, java.util.function.Consumer.class);
            ctor.setAccessible(true);
            java.util.function.Consumer<Object> noop = value -> { };
            return (JComponent) ctor.newInstance(START_FEN, true, stub(builderType), noop, noop);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Builds the Puzzle trainer without loading the on-disk puzzle library.
     *
     * @return puzzle panel
     */
    static JComponent puzzle() {
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
     * Builds the Commands tab builder form.
     *
     * @return commands tab component
     */
    static JComponent commands() {
        application.gui.workbench.command.CommandForm form =
                new application.gui.workbench.command.CommandForm();
        List<application.gui.workbench.command.CommandTemplates.CommandTemplate> templates =
                application.gui.workbench.command.CommandTemplates.commandTemplates();
        application.gui.workbench.command.CommandTemplates.CommandTemplate template = templates.stream()
                .filter(candidate -> "Best move".equals(candidate.name()))
                .findFirst()
                .orElse(templates.get(0));
        form.setTemplate(template, new application.gui.workbench.command.CommandTemplates.TemplateContext(
                START_FEN, "1s", "4", "1", "1", "default.engine.toml", "300", "64"));
        form.expandOptionalFlags();
        return form;
    }

    /**
     * Builds the Network inspector tab.
     *
     * @return network tab component
     */
    static JComponent network() {
        return simple("application.gui.workbench.network.NetworkPanel");
    }

    /**
     * Builds the Logs tab.
     *
     * @return logs tab component
     */
    static JComponent logs() {
        return simple("application.gui.workbench.session.LogPanel");
    }

    /**
     * Builds the Console with a few sample lines.
     *
     * @return console component
     */
    static JComponent console() {
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
     * Builds the MCTS inspector tab.
     *
     * @return MCTS tab component
     */
    static JComponent mcts() {
        try {
            Class<?> sessionType = Class.forName("application.gui.workbench.mcts.MctsSession");
            Object session = sessionType.getDeclaredConstructor().newInstance();
            Supplier<String> fen = () -> START_FEN;
            return (JComponent) Class.forName("application.gui.workbench.mcts.MctsPanel")
                    .getDeclaredConstructor(sessionType, Supplier.class)
                    .newInstance(session, fen);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Builds the Engine "Tree" tab over an empty shared session.
     *
     * @return tree tab component
     */
    static JComponent tree() {
        try {
            Class<?> sessionType = Class.forName("application.gui.workbench.mcts.MctsSession");
            Object session = sessionType.getDeclaredConstructor().newInstance();
            Supplier<String> fen = () -> START_FEN;
            return (JComponent) Class.forName("application.gui.workbench.mcts.TreePanel")
                    .getDeclaredConstructor(sessionType, Supplier.class)
                    .newInstance(session, fen);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Builds the Publish tab.
     *
     * @return publishing tab component
     */
    static JComponent publish() {
        try {
            Class<?> hostType = Class.forName("application.gui.workbench.publish.PublishingPanel$Host");
            Object host = richStub(hostType, Map.of(
                    "currentFen", () -> START_FEN,
                    "reportPanel", JPanel::new));
            Object panel = Class.forName("application.gui.workbench.publish.PublishingPanel")
                    .getDeclaredConstructor(hostType).newInstance(host);
            return asComponent(panel);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Builds a shared board-workspace split pane.
     *
     * @param stage board stage
     * @param rail right-side rail
     * @return split component
     */
    private static JSplitPane boardSplit(JComponent stage, JComponent rail) {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, stage, rail);
        split.setResizeWeight(BOARD_SPLIT_WEIGHT);
        split.setDividerLocation(BOARD_SPLIT_WEIGHT);
        return split;
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
     * Returns a dynamic proxy with per-method overrides, falling back to sensible
     * defaults.
     *
     * @param type interface type
     * @param overrides method-name to value supplier
     * @return the proxy
     */
    private static Object richStub(Class<?> type, Map<String, Supplier<Object>> overrides) {
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
            if (JComponent.class.isAssignableFrom(ret)) {
                return new JPanel();
            }
            if (List.class.isAssignableFrom(ret)) {
                return List.of();
            }
            if (ret.isPrimitive() && ret != void.class) {
                return 0;
            }
            return null;
        });
    }
}
