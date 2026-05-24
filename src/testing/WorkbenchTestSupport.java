package testing;

import java.awt.Component;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;

import application.gui.workbench.ui.Theme;

import chess.core.Field;
import chess.struct.Game;

/**
 * Shared reflective and rendering helpers for workbench regression suites.
 */
@SuppressWarnings("java:S3011")
final class WorkbenchTestSupport {

    /**
     * Prevents instantiation.
     */
    private WorkbenchTestSupport() {
        // utility
    }

    /**
     * Workbench root package.
     */
    static final String WORKBENCH_PACKAGE = "application.gui.workbench.";

    /**
     * Feature packages used by the split workbench implementation.
     */
    static final String[] WORKBENCH_SUBPACKAGES = {
            "audio.",
            "board.",
            "command.",
            "dashboard.",
            "game.",
            "layout.",
            "mcts.",
            "network.",
            "publish.",
            "session.",
            "ui.",
            "window."
    };

    /**
     * Shared standard starting position.
     */
    static final String START_FEN = Game.STANDARD_START_FEN;

    /**
     * Simple mate-in-one position: {@code Qg7#}.
     */
    static final String MATE_IN_ONE_FEN =
            "7k/8/5KQ1/8/8/8/8/8 w - - 0 1";

    /**
     * User-reported forced mate in four for workbench MCTS proof shortcuts.
     */
    static final String FORCED_MATE_IN_FOUR_FEN =
            "7k/3rrrpp/8/8/8/8/PP6/1KR5 w - - 0 1";

    /**
     * Use column index in the command option table.
     */
    static final int COL_USE = 0;

    /**
     * Flag column index in the command option table.
     */
    static final int COL_FLAG = 1;

    /**
     * Value column index in the command option table.
     */
    static final int COL_VALUE = 2;

    /**
     * Creates a reflected board move handler.
     *
     * @param played captured moves
     * @return move handler proxy
     */
    static Object moveHandler(List<Short> played) {
        Class<?> handlerType = type("MoveHandler");
        return Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[] { handlerType },
                (proxy, method, args) -> {
                    if ("play".equals(method.getName())) {
                        played.add((Short) args[0]);
                    }
                    return null;
                });
    }

    /**
     * Finds the first button contained in a component tree.
     *
     * @param component root component
     * @return first button
     */
    static JButton firstButton(Component component) {
        if (component instanceof JButton button) {
            return button;
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return firstButton(child);
                } catch (AssertionError ex) {
                    // Continue searching siblings.
                }
            }
        }
        throw new AssertionError("missing button in " + component.getClass().getName());
    }

    /**
     * Returns whether a component tree contains a label with exact text.
     *
     * @param component root component
     * @param text expected label text
     * @return true when found
     */
    static boolean componentTreeHasLabelText(Component component, String text) {
        if (component instanceof JLabel label && text.equals(label.getText())) {
            return true;
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                if (componentTreeHasLabelText(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether a component tree contains tooltip text.
     *
     * @param component root component
     * @param text expected tooltip fragment
     * @return true when found
     */
    static boolean componentTreeHasTooltip(Component component, String text) {
        if (component instanceof JComponent jComponent) {
            String tooltip = jComponent.getToolTipText();
            if (tooltip != null && tooltip.contains(text)) {
                return true;
            }
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                if (componentTreeHasTooltip(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Paints a component into an ARGB image.
     *
     * @param component component to paint
     * @param width width
     * @param height height
     * @return painted image
     */
    static BufferedImage paint(Component component, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * Sums alpha values over a rectangular image region.
     * @param image image value
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param height height in pixels
     * @return alpha sum result
     */
    static int alphaSum(BufferedImage image, int x, int y, int width, int height) {
        int sum = 0;
        for (int yy = y; yy < y + height; yy++) {
            for (int xx = x; xx < x + width; xx++) {
                sum += new Color(image.getRGB(xx, yy), true).getAlpha();
            }
        }
        return sum;
    }

    /**
     * Returns the highest alpha value painted into an image.
     *
     * @param image image value
     * @return maximum alpha channel value
     */
    static int maxAlpha(BufferedImage image) {
        int max = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                max = Math.max(max, new Color(image.getRGB(x, y), true).getAlpha());
            }
        }
        return max;
    }

    /**
     * Counts pixels whose ARGB values differ between two same-sized images.
     *
     * @param first first image
     * @param second second image
     * @return changed pixel count
     */
    static int changedPixelCount(BufferedImage first, BufferedImage second) {
        assertEquals(Integer.valueOf(first.getWidth()), Integer.valueOf(second.getWidth()), "image width");
        assertEquals(Integer.valueOf(first.getHeight()), Integer.valueOf(second.getHeight()), "image height");
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Verifies each chessboard.js-style coordinate corner receives painted
     * notation.
     *
     * @param withNotation image with coordinates
     * @param withoutNotation image without coordinates
     * @param width board component width
     * @param height board component height
     */
    static void assertBoardNotationCornersChange(
            BufferedImage withNotation,
            BufferedImage withoutNotation,
            int width,
            int height) {
        int size = Math.min(width - 64, height - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (width - size) / 2;
        int boardY = (height - size) / 2;
        for (int i = 0; i < 8; i++) {
            assertTrue(changedPixelCount(withNotation, withoutNotation,
                    boardX + i * cell + cell - 22, boardY + size - 22, 22, 22) > 0,
                    "file coordinate " + (char) ('a' + i) + " painted");
            assertTrue(changedPixelCount(withNotation, withoutNotation,
                    boardX, boardY + i * cell, 22, 22) > 0,
                    "rank coordinate row " + i + " painted");
        }
    }

    /**
     * Counts changed pixels in a rectangular image region.
     *
     * @param first first image
     * @param second second image
     * @param x x coordinate
     * @param y y coordinate
     * @param width region width
     * @param height region height
     * @return changed pixel count
     */
    static int changedPixelCount(
            BufferedImage first,
            BufferedImage second,
            int x,
            int y,
            int width,
            int height) {
        int count = 0;
        for (int yy = y; yy < y + height; yy++) {
            for (int xx = x; xx < x + width; xx++) {
                if (first.getRGB(xx, yy) != second.getRGB(xx, yy)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Paints a component and verifies the top-left pixel was cleared opaquely.
     *
     * @param component component to paint
     * @param width width
     * @param height height
     * @param label assertion label
     */
    static void assertPaintsOpaqueCorner(JComponent component, int width, int height, String label) {
        component.setSize(width, height);
        BufferedImage image = paint(component, width, height);
        int alpha = new Color(image.getRGB(0, 0), true).getAlpha();
        assertEquals(Integer.valueOf(255), Integer.valueOf(alpha), label);
    }

    /**
     * Creates a mouse event for board tests.
     *
     * @param board board component
     * @param id event id
     * @param when event timestamp
     * @param x x coordinate
     * @param y y coordinate
     * @param clicks click count
     * @return mouse event
     */
    static MouseEvent mouse(Component board, int id, long when, int x, int y, int clicks) {
        int modifiers = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.BUTTON1_DOWN_MASK : 0;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        return new MouseEvent(board, id, when, modifiers, x, y, clicks, false, button);
    }

    /**
     * Draws one right-button board arrow with optional modifier bits.
     *
     * @param board board component
     * @param from origin point
     * @param to target point
     * @param extraModifiers modifier bits
     */
    static void drawRightArrow(Component board, Point from, Point to, int extraModifiers) {
        long now = System.currentTimeMillis();
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_PRESSED, now, from.x, from.y, 1, extraModifiers));
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_DRAGGED, now + 1L, to.x, to.y, 0, extraModifiers));
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_RELEASED, now + 2L, to.x, to.y, 1, extraModifiers));
    }

    /**
     * Sends one right-click to a board square.
     *
     * @param board board component
     * @param point click point
     * @param extraModifiers modifier bits
     */
    static void rightClick(Component board, Point point, int extraModifiers) {
        long now = System.currentTimeMillis();
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_PRESSED, now, point.x, point.y, 1, extraModifiers));
        board.dispatchEvent(rightMouse(board, MouseEvent.MOUSE_RELEASED, now + 1L, point.x, point.y, 1,
                extraModifiers));
    }

    /**
     * Creates a right-button mouse event.
     *
     * @param board board component
     * @param id event id
     * @param when timestamp
     * @param x x coordinate
     * @param y y coordinate
     * @param clicks click count
     * @param extraModifiers modifier bits
     * @return mouse event
     */
    static MouseEvent rightMouse(
            Component board, int id, long when, int x, int y, int clicks, int extraModifiers) {
        int modifiers = extraModifiers | (id == MouseEvent.MOUSE_RELEASED ? 0 : MouseEvent.BUTTON3_DOWN_MASK);
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON3;
        return new MouseEvent(board, id, when, modifiers, x, y, clicks, false, button);
    }

    /**
     * Returns the board-center point for one square under the tested board sizing.
     *
     * @param square square index
     * @param whiteDown true when White is down
     * @param width component width
     * @param height component height
     * @return square center point
     */
    static Point boardPoint(byte square, boolean whiteDown, int width, int height) {
        int size = Math.min(width - 64, height - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        int boardX = (width - size) / 2;
        int boardY = (height - size) / 2;
        int file = Field.getX(square);
        int rankRow = square / 8;
        int col = whiteDown ? file : 7 - file;
        int row = whiteDown ? rankRow : 7 - rankRow;
        return new Point(boardX + col * cell + cell / 2, boardY + row * cell + cell / 2);
    }

    /**
     * Samples the top-left inset highlight pixel for one board square.
     *
     * @param board board component
     * @param square square index
     * @return sampled color
     */
    static Color boardSquareEdgeColor(Component board, byte square) {
        int width = board.getWidth();
        int height = board.getHeight();
        int size = Math.min(width - 64, height - 64);
        size = Math.max(64, size - size % 8);
        int cell = size / 8;
        Point center = boardPoint(square, true, width, height);
        return new Color(paint(board, width, height).getRGB(center.x - cell / 2 + 2, center.y - cell / 2 + 2), true);
    }

    /**
     * Samples the center pixel for one board square.
     *
     * @param board board component
     * @param square square index
     * @return sampled color
     */
    static Color boardSquareCenterColor(Component board, byte square) {
        Point center = boardPoint(square, true, board.getWidth(), board.getHeight());
        return new Color(paint(board, board.getWidth(), board.getHeight()).getRGB(center.x, center.y), true);
    }

    /**
     * Creates a command option model for a named template.
     *
     * @param name template name
     * @return populated option table model
     */
    static Object optionsFor(String name) {
        Object template = template(name);
        Object options = construct(type("OptionTableModel"), new Class<?>[0]);
        invoke(options, "setOptions",
                new Class<?>[] { List.class, type("CommandTemplates$TemplateContext") },
                templateOptions(template), templateContext(START_FEN, "2s", "4", "3", "1"));
        return options;
    }

    /**
     * Finds a command template by display name.
     *
     * @param name template name
     * @return command template
     */
    static Object template(String name) {
        DefaultComboBoxModel<?> model = (DefaultComboBoxModel<?>) invokeStatic(type("CommandTemplates"),
                "commandModel", new Class<?>[0]);
        for (int i = 0; i < model.getSize(); i++) {
            Object template = model.getElementAt(i);
            if (name.equals(invoke(template, "name", new Class<?>[0]))) {
                return template;
            }
        }
        throw new AssertionError("missing command template: " + name);
    }

    /**
     * Finds a batch task by display name.
     *
     * @param name task name
     * @return batch task
     */
    static Object batchTask(String name) {
        DefaultComboBoxModel<?> model = (DefaultComboBoxModel<?>) invokeStatic(type("CommandTemplates"),
                "batchModel", new Class<?>[0]);
        for (int i = 0; i < model.getSize(); i++) {
            Object task = model.getElementAt(i);
            if (name.equals(invoke(task, "name", new Class<?>[0]))) {
                return task;
            }
        }
        throw new AssertionError("missing batch task: " + name);
    }

    /**
     * Finds a row by flag label.
     *
     * @param model option model
     * @param flag flag label
     * @return row index
     */
    static int rowForFlag(Object model, String flag) {
        int rowCount = (Integer) invoke(model, "getRowCount", new Class<?>[0]);
        for (int row = 0; row < rowCount; row++) {
            if (flag.equals(invoke(model, "getValueAt", new Class<?>[] { int.class, int.class },
                    row, COL_FLAG))) {
                return row;
            }
        }
        throw new AssertionError("missing flag row: " + flag);
    }

    /**
     * Returns whether a row exists for a flag label.
     *
     * @param model option model
     * @param flag flag label
     * @return true when present
     */
    static boolean hasRowForFlag(Object model, String flag) {
        try {
            rowForFlag(model, flag);
            return true;
        } catch (AssertionError ex) {
            return false;
        }
    }

    /**
     * Returns the current value for a command option flag.
     *
     * @param model option model
     * @param flag flag label
     * @return value
     */
    static String optionValue(Object model, String flag) {
        return String.valueOf(invoke(model, "getValueAt",
                new Class<?>[] { int.class, int.class }, rowForFlag(model, flag), COL_VALUE));
    }

    /**
     * Returns whether an argument list contains a flag.
     *
     * @param args argument list
     * @param flag flag
     * @return true when present
     */
    static boolean hasFlag(List<String> args, String flag) {
        return args.contains(flag);
    }

    /**
     * Returns the token immediately after a flag in an argument list.
     *
     * @param args argument list
     * @param flag flag
     * @return following value, or empty string
     */
    static String valueAfterFlag(List<String> args, String flag) {
        int index = args.indexOf(flag);
        if (index < 0 || index + 1 >= args.size()) {
            return "";
        }
        return args.get(index + 1);
    }

    /**
     * Returns command args enabled in an option model.
     *
     * @param options option model
     * @return enabled arguments
     */
    @SuppressWarnings("unchecked")
    static List<String> enabledArgs(Object options) {
        return (List<String>) invoke(options, "enabledArgs", new Class<?>[0]);
    }

    /**
     * Reads a template's option metadata.
     *
     * @param template command template
     * @return option metadata
     */
    @SuppressWarnings("unchecked")
    static List<Object> templateOptions(Object template) {
        return (List<Object>) invoke(template, "options", new Class<?>[0]);
    }

    /**
     * Creates a workbench template context.
     *
     * @param fen FEN
     * @param duration duration
     * @param depth depth
     * @param multipv MultiPV
     * @param threads threads
     * @return template context
     */
    static Object templateContext(String fen, String duration, String depth, String multipv, String threads) {
        return templateContext(fen, duration, depth, multipv, threads, "config/default.engine.toml", "", "");
    }

    /**
     * Creates a workbench template context.
     *
     * @param fen FEN
     * @param duration duration
     * @param depth depth
     * @param multipv MultiPV
     * @param threads threads
     * @param protocolPath engine protocol path
     * @param nodes node budget
     * @param hash hash MB
     * @return template context
     */
    static Object templateContext(String fen, String duration, String depth, String multipv, String threads,
            String protocolPath, String nodes, String hash) {
        return construct(type("CommandTemplates$TemplateContext"),
                new Class<?>[] { String.class, String.class, String.class, String.class, String.class,
                        String.class, String.class, String.class },
                fen, duration, depth, multipv, threads, protocolPath, nodes, hash);
    }

    /**
     * Runs the workbench option-filter matcher.
     *
     * @param query query text
     * @param values searchable values
     * @return true when the query matches
     */
    static boolean optionFilterMatches(String query, String... values) {
        return (Boolean) invokeStatic(type("Window"), "optionFilterMatches",
                new Class<?>[] { String.class, String[].class }, query, values);
    }

    /**
     * Runs the workbench engine-output parser.
     *
     * @param output command output
     * @return parsed eval record
     */
    static Object parseEngineEval(String output) {
        return invokeStatic(type("Window"), "parseEngineEval", new Class<?>[] { String.class }, output);
    }

    /**
     * Reads the mate flag from a parsed eval.
     *
     * @param eval parsed eval
     * @return mate flag
     */
    static boolean engineEvalMate(Object eval) {
        return (Boolean) invoke(eval, "mate", new Class<?>[0]);
    }

    /**
     * Reads the score from a parsed eval.
     *
     * @param eval parsed eval
     * @return score
     */
    static int engineEvalValue(Object eval) {
        return (Integer) invoke(eval, "value", new Class<?>[0]);
    }

    /**
     * Formats a centipawn value through the workbench eval bar.
     *
     * @param centipawns centipawns
     * @return formatted score
     */
    static String formatCentipawns(int centipawns) {
        return (String) invokeStatic(type("EvalBar"), "formatCentipawns",
                new Class<?>[] { int.class }, centipawns);
    }

    /**
     * Maps centipawns to white's bar share through the workbench eval bar.
     *
     * @param centipawns centipawns
     * @return white share
     */
    static double whiteShareForCentipawns(int centipawns) {
        return (Double) invokeStatic(type("EvalBar"), "whiteShareForCentipawns",
                new Class<?>[] { int.class }, centipawns);
    }

    /**
     * Eases animation progress through the workbench eval bar.
     *
     * @param progress linear progress
     * @return eased progress
     */
    static double evalEase(double progress) {
        return (Double) invokeStatic(type("EvalBar"), "easeInOutCubic",
                new Class<?>[] { double.class }, progress);
    }

    /**
     * Reads one color token from the workbench theme.
     *
     * @param name field name
     * @return color token
     */
    static Color themeColor(String name) {
        return (Color) staticField(type("Theme"), name);
    }

    /**
     * Finds the first line color in a Swing border tree.
     *
     * @param border border
     * @return first line color, or null when no line color exists
     */
    static Color firstBorderColor(Border border) {
        if (border instanceof LineBorder line) {
            return line.getLineColor();
        }
        if (border instanceof MatteBorder matte) {
            return matte.getMatteColor();
        }
        if (border instanceof CompoundBorder compound) {
            Color outside = firstBorderColor(compound.getOutsideBorder());
            return outside == null ? firstBorderColor(compound.getInsideBorder()) : outside;
        }
        return null;
    }

    /**
     * Loads a workbench implementation class.
     *
     * @param name simple or nested class name
     * @return class
     */
    static Class<?> type(String name) {
        ClassNotFoundException first = null;
        try {
            return Class.forName(WORKBENCH_PACKAGE + name);
        } catch (ClassNotFoundException ex) {
            first = ex;
        }
        for (String subpackage : WORKBENCH_SUBPACKAGES) {
            try {
                return Class.forName(WORKBENCH_PACKAGE + subpackage + name);
            } catch (ClassNotFoundException ignored) {
                // Try the next feature package.
            }
        }
        throw new AssertionError("missing workbench type " + name, first);
    }

    /**
     * Constructs a package-private workbench type.
     *
     * @param type type
     * @param parameterTypes constructor parameter types
     * @param args constructor arguments
     * @return instance
     */
    static Object construct(Class<?> type, Class<?>[] parameterTypes, Object... args) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (InvocationTargetException ex) {
            rethrowCause(ex);
            throw new AssertionError("unreachable");
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not construct " + type.getName(), ex);
        }
    }

    /**
     * Invokes a package-static method.
     *
     * @param owner declaring type
     * @param name method name
     * @param parameterTypes parameter types
     * @param args method arguments
     * @return method result
     */
    static Object invokeStatic(Class<?> owner, String name, Class<?>[] parameterTypes, Object... args) {
        return invokeMethod(null, owner, name, parameterTypes, args);
    }

    /**
     * Invokes a package-private instance method.
     *
     * @param target target instance
     * @param name method name
     * @param parameterTypes parameter types
     * @param args method arguments
     * @return method result
     */
    static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        return invokeMethod(target, target.getClass(), name, parameterTypes, args);
    }

    /**
     * Invokes a package-private instance method declared on a superclass.
     *
     * @param owner declaring type
     * @param target target instance
     * @param name method name
     * @param parameterTypes parameter types
     * @param args method arguments
     * @return method result
     */
    static Object invokeOn(
            Class<?> owner,
            Object target,
            String name,
            Class<?>[] parameterTypes,
            Object... args) {
        return invokeMethod(target, owner, name, parameterTypes, args);
    }

    /**
     * Looks up a package-private enum constant.
     *
     * @param type enum type
     * @param name constant name
     * @return enum value
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    /**
     * Reads a private field from a package-private workbench object.
     *
     * @param target target instance
     * @param name field name
     * @return field value
     */
    static Object field(Object target, String name) {
        try {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    java.lang.reflect.Field reflectedField = type.getDeclaredField(name);
                    reflectedField.setAccessible(true);
                    return reflectedField.get(target);
                } catch (NoSuchFieldException ex) {
                    type = type.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not read " + target.getClass().getName() + "." + name, ex);
        }
    }

    /**
     * Writes a private field on a package-private workbench object.
     *
     * @param target target instance
     * @param name field name
     * @param value replacement value
     */
    static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field reflectedField = target.getClass().getDeclaredField(name);
            reflectedField.setAccessible(true);
            reflectedField.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not write " + target.getClass().getName() + "." + name, ex);
        }
    }

    /**
     * Reads a static field from a package-private workbench class.
     *
     * @param owner declaring type
     * @param name field name
     * @return field value
     */
    static Object staticField(Class<?> owner, String name) {
        try {
            java.lang.reflect.Field reflectedField = owner.getDeclaredField(name);
            reflectedField.setAccessible(true);
            return reflectedField.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not read " + owner.getName() + "." + name, ex);
        }
    }

    /**
     * Invokes a reflected method.
     *
     * @param target target instance or null for static methods
     * @param owner declaring type
     * @param name method name
     * @param parameterTypes parameter types
     * @param args method arguments
     * @return method result
     */
    static Object invokeMethod(
            Object target,
            Class<?> owner,
            String name,
            Class<?>[] parameterTypes,
            Object... args) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            rethrowCause(ex);
            throw new AssertionError("unreachable");
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not invoke " + owner.getName() + "." + name, ex);
        }
    }

    /**
     * Preserves the original failure thrown by a reflected method.
     *
     * @param ex invocation wrapper
     */
    static void rethrowCause(InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new AssertionError(cause);
    }

    /**
     * Verifies a condition is true.
     *
     * @param condition condition
     * @param label assertion label
     */
    static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label + ": expected true");
        }
    }

    /**
     * Verifies a condition is false.
     *
     * @param condition condition
     * @param label assertion label
     */
    static void assertFalse(boolean condition, String label) {
        if (condition) {
            throw new AssertionError(label + ": expected false");
        }
    }

    /**
     * Verifies floating-point closeness.
     *
     * @param expected expected value
     * @param actual actual value
     * @param tolerance accepted absolute tolerance
     * @param label assertion label
     */
    static void assertClose(double expected, double actual, double tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    /**
     * Verifies two float arrays are bit-identical.
     *
     * @param expected expected values
     * @param actual actual values
     * @param label assertion label
     */
    static void assertFloatArrayExact(float[] expected, float[] actual, String label) {
        if (expected == null || actual == null || expected.length != actual.length) {
            throw new AssertionError(label + ": array length mismatch");
        }
        for (int i = 0; i < expected.length; i++) {
            if (Float.floatToIntBits(expected[i]) != Float.floatToIntBits(actual[i])) {
                throw new AssertionError(label + ": mismatch at " + i
                        + ", expected " + expected[i] + ", got " + actual[i]);
            }
        }
    }

    /**
     * Verifies a theme foreground/background pair has sufficient contrast.
     *
     * @param label assertion label
     * @param foregroundName foreground token name
     * @param backgroundName background token name
     * @param minimumRatio minimum accepted contrast ratio
     */
    static void assertThemeContrast(String label, String foregroundName, String backgroundName,
            double minimumRatio) {
        Color foreground = themeColor(foregroundName);
        Color background = themeColor(backgroundName);
        double ratio = contrastRatio(foreground, background);
        if (ratio < minimumRatio) {
            throw new AssertionError(label + ": contrast " + ratio + " below " + minimumRatio
                    + " for " + foregroundName + " on " + backgroundName);
        }
    }

    /**
     * Escapes the subset needed for string-containing JSON assertions.
     *
     * @param value raw value
     * @return escaped value
     */
    static String jsonEsc(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Calculates WCAG contrast ratio for two opaque colors.
     *
     * @param first first color
     * @param second second color
     * @return contrast ratio
     */
    static double contrastRatio(Color first, Color second) {
        double firstLum = relativeLuminance(first);
        double secondLum = relativeLuminance(second);
        double light = Math.max(firstLum, secondLum);
        double dark = Math.min(firstLum, secondLum);
        return (light + 0.05) / (dark + 0.05);
    }

    /**
     * Calculates relative luminance for an sRGB color.
     *
     * @param color color
     * @return relative luminance
     */
    static double relativeLuminance(Color color) {
        return 0.2126 * linearChannel(color.getRed())
                + 0.7152 * linearChannel(color.getGreen())
                + 0.0722 * linearChannel(color.getBlue());
    }

    /**
     * Converts one sRGB channel to linear light.
     *
     * @param channel 0..255 channel value
     * @return linearized channel
     */
    static double linearChannel(int channel) {
        double normalized = channel / 255.0;
        return normalized <= 0.03928
                ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    /**
     * Verifies two colors are visually separated enough for a non-text mark.
     *
     * @param first first color
     * @param second second color
     * @param minimum minimum Euclidean RGB distance
     * @param label assertion label
     */
    static void assertColorDistanceAtLeast(Color first, Color second, double minimum, String label) {
        double distance = colorDistance(first, second);
        if (distance < minimum) {
            throw new AssertionError(label + ": distance " + distance + " below " + minimum);
        }
    }

    /**
     * Calculates Euclidean RGB distance.
     *
     * @param first first color
     * @param second second color
     * @return RGB distance
     */
    static double colorDistance(Color first, Color second) {
        int red = first.getRed() - second.getRed();
        int green = first.getGreen() - second.getGreen();
        int blue = first.getBlue() - second.getBlue();
        return Math.sqrt((double) red * red + (double) green * green + (double) blue * blue);
    }

    /**
     * Verifies exact RGB color equality.
     *
     * @param expected expected color
     * @param actual actual color
     * @param label assertion label
     */
    static void assertColor(Color expected, Color actual, String label) {
        if (expected.getRGB() != actual.getRGB()) {
            throw new AssertionError(label + ": expected " + colorText(expected) + ", got " + colorText(actual));
        }
    }

    /**
     * Returns a compact RGB color label.
     *
     * @param color color
     * @return RGB label
     */
    static String colorText(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Verifies object equality.
     *
     * @param expected expected value
     * @param actual actual value
     * @param label assertion label
     */
    static void assertEquals(Object expected, Object actual, String label) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
