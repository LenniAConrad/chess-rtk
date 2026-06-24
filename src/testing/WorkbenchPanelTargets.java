package testing;

import application.gui.workbench.window.Window;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Resolves debug target names to Workbench tabs and nested modes.
 */
final class WorkbenchPanelTargets {

    /**
     * Offscreen targets rendered by {@link WorkbenchShots}.
     */
    static final String OFFSCREEN_TARGETS = "dashboard,datasets,datasets-loaded,play,draw,analyze,puzzle,"
            + "commands,console,network,mcts,tree,logs,publish";

    /**
     * Live-window targets accepted by preview/capture helpers.
     */
    static final String LIVE_TARGETS = "dashboard,board,analyze,play,solve,puzzle,relations,draw,run,commands,"
            + "datasets,publish,engine,evaluator,network,search,mcts,tree,gauntlet,console,logs,"
            + "board:analyze,board:play,board:solve,board:relations,board:draw,engine:evaluator,"
            + "engine:search,engine:tree,engine:gauntlet";

    /**
     * Top-level dashboard tab index.
     */
    private static final int TAB_DASHBOARD = 0;

    /**
     * Top-level command/run tab index.
     */
    private static final int TAB_RUN = 2;

    /**
     * Top-level datasets tab index.
     */
    private static final int TAB_DATASETS = 3;

    /**
     * Top-level publishing tab index.
     */
    private static final int TAB_PUBLISH = 4;

    /**
     * Top-level console tab index.
     */
    private static final int TAB_CONSOLE = 6;

    /**
     * Top-level logs tab index.
     */
    private static final int TAB_LOGS = 7;

    /**
     * Board analysis mode index.
     */
    private static final int BOARD_ANALYZE = 0;

    /**
     * Board play mode index.
     */
    private static final int BOARD_PLAY = 1;

    /**
     * Board puzzle-solving mode index.
     */
    private static final int BOARD_SOLVE = 2;

    /**
     * Board relation-inspection mode index.
     */
    private static final int BOARD_RELATIONS = 3;

    /**
     * Board drawing mode index.
     */
    private static final int BOARD_DRAW = 4;

    /**
     * Engine network/evaluator mode index.
     */
    private static final int ENGINE_NETWORK = 0;

    /**
     * Engine search mode index.
     */
    private static final int ENGINE_SEARCH = 1;

    /**
     * Engine gauntlet mode index.
     */
    private static final int ENGINE_GAUNTLET = 2;

    /**
     * Prevents instantiation.
     */
    private WorkbenchPanelTargets() {
        // utility
    }

    /**
     * Selects a target in a live Workbench frame.
     *
     * @param frame Workbench frame
     * @param target target name, or blank to keep the default tab
     */
    static void select(Window frame, String target) {
        if (frame == null) {
            return;
        }
        String key = normalize(target);
        if (key.isBlank() || "default".equals(key)) {
            return;
        }
        switch (key) {
            case "dashboard", "home" -> selectTab(frame, TAB_DASHBOARD);
            case "board", "analyze", "analysis", "board:analyze", "board-analysis" ->
                    openBoard(frame, BOARD_ANALYZE);
            case "play", "board:play", "board-play" -> openBoard(frame, BOARD_PLAY);
            case "solve", "puzzle", "puzzles", "board:solve", "board:puzzle", "board-solve", "board-puzzle" ->
                    openBoard(frame, BOARD_SOLVE);
            case "relations", "relation", "board:relations", "board-relations" ->
                    openBoard(frame, BOARD_RELATIONS);
            case "draw", "annotate", "board:draw", "board-draw" -> openBoard(frame, BOARD_DRAW);
            case "run", "commands", "command", "build", "run:build", "run-build" -> selectTab(frame, TAB_RUN);
            case "datasets", "dataset", "data" -> selectTab(frame, TAB_DATASETS);
            case "publish", "publishing" -> selectTab(frame, TAB_PUBLISH);
            case "engine", "engine-lab", "evaluator", "network", "engine:evaluator", "engine:network",
                    "engine-evaluator", "engine-network" -> openEngine(frame, ENGINE_NETWORK);
            case "search", "mcts", "engine:search", "engine:mcts", "engine-search", "engine-mcts" ->
                    openEngine(frame, ENGINE_SEARCH);
            case "tree", "engine:tree", "engine-tree" -> openEngineGraph(frame);
            case "gauntlet", "selfplay", "self-play", "engine:gauntlet", "engine-gauntlet" ->
                    openEngine(frame, ENGINE_GAUNTLET);
            case "console" -> selectTab(frame, TAB_CONSOLE);
            case "logs", "log" -> selectTab(frame, TAB_LOGS);
            default -> throw new IllegalArgumentException("Unknown Workbench panel target: " + target
                    + "\nKnown live targets: " + LIVE_TARGETS);
        }
    }

    /**
     * Normalizes a target token while preserving group separators.
     *
     * @param target raw target token
     * @return normalized target token
     */
    private static String normalize(String target) {
        if (target == null) {
            return "";
        }
        return target.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-')
                .replace('/', ':')
                .replace('.', ':');
    }

    /**
     * Selects a top-level Workbench tab.
     *
     * @param frame Workbench frame
     * @param tab tab index
     */
    private static void selectTab(Window frame, int tab) {
        invoke(frame, "selectTab", tab);
    }

    /**
     * Opens a Board workspace mode.
     *
     * @param frame Workbench frame
     * @param mode board mode index
     */
    private static void openBoard(Window frame, int mode) {
        invoke(frame, "openBoard", mode);
    }

    /**
     * Opens an Engine workspace mode.
     *
     * @param frame Workbench frame
     * @param mode engine mode index
     */
    private static void openEngine(Window frame, int mode) {
        invoke(frame, "openEngine", mode);
    }

    /**
     * Opens the Engine Search workspace on its graph subview.
     *
     * @param frame Workbench frame
     */
    private static void openEngineGraph(Window frame) {
        invoke(frame, "openEngineGraph");
    }

    /**
     * Invokes a protected single-int Workbench method.
     *
     * @param frame Workbench frame
     * @param name method name
     * @param value integer argument
     */
    private static void invoke(Window frame, String name, int value) {
        invoke(frame, name, new Class<?>[] { int.class }, Integer.valueOf(value));
    }

    /**
     * Invokes a protected no-argument Workbench method.
     *
     * @param frame Workbench frame
     * @param name method name
     */
    private static void invoke(Window frame, String name) {
        invoke(frame, name, new Class<?>[0]);
    }

    /**
     * Invokes a protected Workbench method.
     *
     * @param frame Workbench frame
     * @param name method name
     * @param parameterTypes method parameter types
     * @param args invocation arguments
     */
    private static void invoke(Window frame, String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = findMethod(frame.getClass(), name, parameterTypes);
            method.setAccessible(true);
            method.invoke(frame, args);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not access Workbench target method: " + name, ex);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new IllegalStateException("Workbench target method failed: " + name, cause);
        }
    }

    /**
     * Finds a method on the class hierarchy.
     *
     * @param type starting class
     * @param name method name
     * @param parameterTypes method parameter types
     * @return matching method
     */
    private static Method findMethod(Class<?> type, String name, Class<?>[] parameterTypes) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ex) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new IllegalStateException("Workbench target method not found: " + name);
    }
}
