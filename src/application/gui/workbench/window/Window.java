/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.window;

import application.Config;
import application.gui.workbench.command.CommandPalette.PaletteAction;
import application.gui.workbench.command.CommandTemplates.CommandTemplate;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import application.gui.workbench.game.EngineEval;
import application.gui.workbench.game.FenInput;
import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.Theme;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.tag.Generator;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Evaluation;
import chess.uci.Output;
import chess.uci.Protocol;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;

import static application.gui.workbench.command.CommandArgs.addOptionalPositiveIntegerArg;
import static application.gui.workbench.command.CommandArgs.addOptionalTextArg;
import static application.gui.workbench.ui.SwingTasks.runAsync;
import static application.gui.workbench.ui.Ui.addVerticalFiller;
import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.buttonRow;
import static application.gui.workbench.ui.Ui.changeListener;
import static application.gui.workbench.ui.Ui.collapsible;
import static application.gui.workbench.ui.Ui.constraints;
import static application.gui.workbench.ui.Ui.fillViewport;
import static application.gui.workbench.ui.Ui.flow;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.iconButton;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.optionGroup;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.showConfirmDialog;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.styleSpinners;
import static application.gui.workbench.ui.Ui.tabbedPane;
import static application.gui.workbench.ui.Ui.titled;
import static application.gui.workbench.ui.Ui.transparentPanel;
import static application.gui.workbench.ui.Ui.trimmed;
import static application.gui.workbench.ui.Ui.withTooltip;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */
@SuppressWarnings("java:S6539")

public final class Window extends WindowCommandLayer {

    /** Serialization identifier for Swing frame compatibility. */
    private static final long serialVersionUID = 1L;

    /** Dashboard tab index. */
    private static final int TAB_DASHBOARD = 0;

    /** Analysis tab index. */
    private static final int TAB_ANALYZE = 1;

    /** Eval debounce in milliseconds. */
    private static final int EVAL_DEBOUNCE_MS = 90;

    /**
     * Creates the workbench window.
     * @param initialFen initial FEN to load
     * @param whiteDown true when white is rendered at the bottom */
    public Window(String initialFen, boolean whiteDown) {
        setTitle("ChessRTK Workbench");
        loadThemeSetting();
        Theme.install();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 760));
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
        startNewGame(initialFen);
        generateReport();
        setVisible(true);
    }

    /** Returns the first FEN line in free-form text.
     * @param text source text
     * @return first FEN-looking line, or an empty string when none is present */
    protected static String firstFenLine(String text) {
        return WindowGameLayer.firstFenLine(text);
    }

    /** Validates batch FEN input.
     * @param text source text
     * @return batch FEN validation summary */
    protected static FenInput.Summary validateBatchFenInput(String text) {
        return WindowGameLayer.validateBatchFenInput(text);
    }

    /** Compacts a FEN for preview display.
     * @param fen FEN string
     * @return compact FEN preview string */
    protected static String compactFenPreview(String fen) {
        return WindowGameLayer.compactFenPreview(fen);
    }

    /** Formats live-engine status text.
     * @param output command or engine output text
     * @param bestMove best move reported by the engine
     * @return formatted live-engine status text */
    protected static String formatLiveEngineStatus(Output output, short bestMove) {
        return WindowEngineLayer.formatLiveEngineStatus(output, bestMove);
    }

    /** Parses an optional positive integer text field.
     * @param field text field to parse
     * @param label field label used in validation messages
     * @return parsed positive integer, or null when the field is empty */
    protected static Integer optionalPositiveInteger(JTextField field, String label) {
        return WindowEngineLayer.optionalPositiveInteger(field, label);
    }

    /** Returns whether position navigation should route to the board.
     * @param focusOwner current keyboard focus owner
     * @return true when position navigation should route to the board */
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

    /** Returns whether an option matches the command-option filter query.
     * @param query filter query
     * @param values candidate values
     * @return true when the values match the filter query */
    public static boolean optionFilterMatches(String query, String... values) {
        return WindowCommandLayer.optionFilterMatches(query, values);
    }

    /** Parses engine eval text.
     * @param output command or engine output text
     * @return parsed engine evaluation */
    protected static EngineEval parseEngineEval(String output) {
        return WindowCommandLayer.parseEngineEval(output);
    }
}
