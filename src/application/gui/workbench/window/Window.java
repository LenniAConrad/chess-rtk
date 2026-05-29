package application.gui.workbench.window;

import application.gui.workbench.game.EngineEval;
import application.gui.workbench.game.FenInput;
import application.gui.workbench.ui.Theme;
import chess.uci.Analysis;
import chess.uci.Output;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import static application.gui.workbench.ui.Ui.label;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */

public final class Window extends WindowCommandLayer {

    /**
     * Serialization identifier for Swing frame compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Dashboard tab index.
     */
    private static final int TAB_DASHBOARD = 0;

    /**
     * Analysis tab index.
     */
    private static final int TAB_ANALYZE = 1;

    /**
     * Eval debounce in milliseconds.
     */
    private static final int EVAL_DEBOUNCE_MS = 90;

    /**
     * Creates the workbench window.
     * @param initialFen initial FEN to load
     * @param whiteDown true when white is rendered at the bottom
     */
    public Window(String initialFen, boolean whiteDown) {
        setTitle("Workbench");
        loadThemeSetting();
        Theme.install();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT));
        restoreWindowGeometry();
        loadDisplaySettings();
        loadEngineSettings();
        installFieldPlaceholders();
        installEngineSettingListeners();
        buildUi();
        if (Theme.isDark()) {
            Theme.refreshComponentTree(this);
        }
        applyDisplaySettings(false);
        board.setWhiteDown(whiteDown);
        setVisible(true);
        SwingUtilities.invokeLater(() -> finishStartup(initialFen));
    }

    /**
     * Loads the initial position after the first frame has been shown.
     *
     * @param initialFen initial FEN to load
     */
    private void finishStartup(String initialFen) {
        if (!isDisplayable()) {
            return;
        }
        startNewGame(initialFen);
        if (publishingPanel != null) {
            generateReport();
        }
    }

    /**
     * Returns the first FEN line in free-form text.
     * @param text source text
     * @return first FEN-looking line, or an empty string when none is present
     */
    protected static String firstFenLine(String text) {
        return WindowGameLayer.firstFenLine(text);
    }

    /**
     * Validates batch FEN input.
     * @param text source text
     * @return batch FEN validation summary
     */
    protected static FenInput.Summary validateBatchFenInput(String text) {
        return WindowGameLayer.validateBatchFenInput(text);
    }

    /**
     * Compacts a FEN for preview display.
     * @param fen FEN string
     * @return compact FEN preview string
     */
    protected static String compactFenPreview(String fen) {
        return WindowGameLayer.compactFenPreview(fen);
    }

    /**
     * Formats live-engine status text.
     * @param output command or engine output text
     * @param bestMove best move reported by the engine
     * @return formatted live-engine status text
     */
    protected static String formatLiveEngineStatus(Output output, short bestMove) {
        return WindowEngineLayer.formatLiveEngineStatus(output, bestMove);
    }

    /**
     * Parses an optional positive integer text field.
     * @param field text field to parse
     * @param label field label used in validation messages
     * @return parsed positive integer, or null when the field is empty
     */
    protected static Integer optionalPositiveInteger(JTextField field, String label) {
        return WindowEngineLayer.optionalPositiveInteger(field, label);
    }

    /**
     * Returns whether position navigation should route to the board.
     * @param focusOwner current keyboard focus owner
     * @return true when position navigation should route to the board
     */
    protected static boolean shouldRoutePositionNavigation(Component focusOwner) {
        return WindowLifecycle.shouldRoutePositionNavigation(focusOwner);
    }

    /**
     * Returns all key strokes that route to position navigation.
     *
     * @return copied key-stroke array
     */
    protected static KeyStroke[] allPositionNavigationKeyStrokes() {
        return WindowLifecycle.allPositionNavigationKeyStrokes();
    }

    /**
     * Returns whether an option matches the command-option filter query.
     * @param query filter query
     * @param values candidate values
     * @return true when the values match the filter query
     */
    public static boolean optionFilterMatches(String query, String... values) {
        return WindowCommandLayer.optionFilterMatches(query, values);
    }

    /**
     * Parses engine eval text.
     * @param output command or engine output text
     * @return parsed engine evaluation
     */
    protected static EngineEval parseEngineEval(String output) {
        return WindowCommandLayer.parseEngineEval(output);
    }
}
