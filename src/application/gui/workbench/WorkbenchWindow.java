package application.gui.workbench;

import static application.gui.workbench.WorkbenchCommandArgs.addOptionalPositiveIntegerArg;
import static application.gui.workbench.WorkbenchCommandArgs.addOptionalTextArg;
import static application.gui.workbench.WorkbenchUi.addVerticalFiller;
import static application.gui.workbench.WorkbenchUi.button;
import static application.gui.workbench.WorkbenchUi.buttonRow;
import static application.gui.workbench.WorkbenchUi.changeListener;
import static application.gui.workbench.WorkbenchUi.collapsible;
import static application.gui.workbench.WorkbenchUi.constraints;
import static application.gui.workbench.WorkbenchUi.flow;
import static application.gui.workbench.WorkbenchUi.fillViewport;
import static application.gui.workbench.WorkbenchUi.grid;
import static application.gui.workbench.WorkbenchUi.iconButton;
import static application.gui.workbench.WorkbenchUi.label;
import static application.gui.workbench.WorkbenchUi.optionGroup;
import static application.gui.workbench.WorkbenchUi.placeholder;
import static application.gui.workbench.WorkbenchUi.scroll;
import static application.gui.workbench.WorkbenchUi.showConfirmDialog;
import static application.gui.workbench.WorkbenchUi.styleAreas;
import static application.gui.workbench.WorkbenchUi.styleFields;
import static application.gui.workbench.WorkbenchUi.styleSpinners;
import static application.gui.workbench.WorkbenchUi.tabbedPane;
import static application.gui.workbench.WorkbenchUi.titled;
import static application.gui.workbench.WorkbenchUi.trimmed;
import static application.gui.workbench.WorkbenchUi.transparentPanel;
import static application.gui.workbench.WorkbenchUi.withTooltip;
import static application.gui.workbench.WorkbenchSwingTasks.runAsync;

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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.DefaultListModel;
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
import javax.swing.ButtonGroup;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;

import application.Config;
import application.gui.workbench.WorkbenchCommandTemplates.CommandTemplate;
import application.gui.workbench.WorkbenchCommandTemplates.TemplateContext;
import application.gui.workbench.WorkbenchCommandPalette.PaletteAction;
import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.layout.SplitPaneStyler;
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

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */
@SuppressWarnings("java:S6539")

public final class WorkbenchWindow extends WorkbenchWindowCommandLayer {

    /** Serialization identifier for Swing frame compatibility. */
    private static final long serialVersionUID = 1L;

    /** Dashboard tab index. */
    private static final int TAB_DASHBOARD = 0;

    /** Analysis tab index. */
    private static final int TAB_ANALYZE = 1;

    /** Eval debounce in milliseconds. */
    private static final int EVAL_DEBOUNCE_MS = 90;

    public WorkbenchWindow(String initialFen, boolean whiteDown) {
        setTitle("ChessRTK Workbench");
        WorkbenchTheme.install();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 760));
        restoreWindowGeometry();
        loadDisplaySettings();
        loadEngineSettings();
        installFieldPlaceholders();
        installEngineSettingListeners();
        buildUi();
        applyDisplaySettings(false);
        board.setWhiteDown(whiteDown);
        startNewGame(initialFen);
        generateReport();
        setVisible(true);
    }

    /** Returns the first FEN line in free-form text. */
    protected static String firstFenLine(String text) {
        return WorkbenchWindowGameLayer.firstFenLine(text);
    }

    /** Validates batch FEN input. */
    protected static WorkbenchFenInput.Summary validateBatchFenInput(String text) {
        return WorkbenchWindowGameLayer.validateBatchFenInput(text);
    }

    /** Compacts a FEN for preview display. */
    protected static String compactFenPreview(String fen) {
        return WorkbenchWindowGameLayer.compactFenPreview(fen);
    }

    /** Formats live-engine status text. */
    protected static String formatLiveEngineStatus(Output output, short bestMove) {
        return WorkbenchWindowEngineLayer.formatLiveEngineStatus(output, bestMove);
    }

    /** Parses an optional positive integer text field. */
    protected static Integer optionalPositiveInteger(JTextField field, String label) {
        return WorkbenchWindowEngineLayer.optionalPositiveInteger(field, label);
    }

    /** Returns whether position navigation should route to the board. */
    protected static boolean shouldRoutePositionNavigation(Component focusOwner) {
        return WorkbenchWindowLifecycle.shouldRoutePositionNavigation(focusOwner);
    }

    /** Returns whether an option matches the command-option filter query. */
    protected static boolean optionFilterMatches(String query, String... values) {
        return WorkbenchWindowCommandLayer.optionFilterMatches(query, values);
    }

    /** Parses engine eval text. */
    protected static WorkbenchEngineEval parseEngineEval(String output) {
        return WorkbenchWindowCommandLayer.parseEngineEval(output);
    }
}
