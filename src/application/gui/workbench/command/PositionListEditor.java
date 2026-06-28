package application.gui.workbench.command;

import application.gui.workbench.command.CommandTemplates.BatchInputKind;
import application.gui.workbench.game.FenInput;
import application.gui.workbench.game.GameModel;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.Theme;
import chess.core.Setup;
import chess.struct.Game;
import chess.struct.Pgn;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import utility.CommandLine;

import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.changeListener;
import static application.gui.workbench.ui.Ui.controlRow;
import static application.gui.workbench.ui.Ui.emptyState;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * The multi-line input-positions editor revealed inside the Build command form
 * when a batch-style command (one that consumes a {@code --input} file) is
 * selected. One FEN — or one CRTK command — per line, with an empty-state card,
 * a live validity summary, and buttons to add the current board, add a random
 * legal position, import positions from PGN files, or clear the list.
 *
 * <p>This is the former standalone Batch panel's input surface, lifted into a
 * reusable component so the single Build form covers both single- and
 * multi-position commands.</p>
 */
public final class PositionListEditor {

    /**
     * Host callbacks the editor needs from the owning window.
     */
    public interface Context {

        /**
         * Returns the current board FEN.
         *
         * @return current FEN
         */
        String currentFen();

        /**
         * Reports an error to the user.
         *
         * @param title error title
         * @param message error message
         */
        void showError(String title, String message);

        /**
         * Returns a component to own dialogs.
         *
         * @return dialog parent
         */
        Component dialogParent();
    }

    /**
     * Preferred editor height inside the scrolling command form.
     */
    private static final int EDITOR_HEIGHT = 168;

    /**
     * Host callbacks.
     */
    private final transient Context context;

    /**
     * Root component.
     */
    private final JPanel component = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));

    /**
     * One-position-per-line text area.
     */
    private final JTextArea input = new JTextArea();

    /**
     * Empty-state ↔ editor card.
     */
    private final JPanel inputCard = new JPanel(new CardLayout());

    /**
     * Live validity summary.
     */
    private final JLabel status = new JLabel();

    /**
     * Button that appends the current board FEN.
     */
    private final JButton addFenButton;

    /**
     * Button that appends a random legal position.
     */
    private final JButton randomButton;

    /**
     * Button that imports positions from PGN files.
     */
    private final JButton importButton;

    /**
     * Current input mode.
     */
    private transient BatchInputKind mode = BatchInputKind.FEN_LINES;

    /**
     * Change listener invoked whenever the positions text changes.
     */
    private transient Runnable changeListener = () -> {
        // no-op until wired
    };

    /**
     * Whether a status refresh is already queued on the event thread.
     */
    private transient boolean statusUpdateQueued;

    /**
     * Creates a position-list editor.
     *
     * @param context host callbacks
     */
    public PositionListEditor(Context context) {
        this.context = context;
        JPanel top = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        top.add(Theme.section("Input positions"), BorderLayout.WEST);
        Theme.foreground(status, Theme.ForegroundRole.MUTED);
        status.setFont(Theme.font(12, Font.PLAIN));
        status.setHorizontalAlignment(SwingConstants.RIGHT);
        top.add(status, BorderLayout.CENTER);
        component.add(top, BorderLayout.NORTH);

        styleAreas(input);
        input.getDocument().addDocumentListener(changeListener(this::onInputChanged));
        inputCard.setOpaque(false);
        JComponent editorScroll = scroll(input);
        editorScroll.setPreferredSize(new Dimension(editorScroll.getPreferredSize().width, EDITOR_HEIGHT));
        inputCard.add(editorScroll, "input");
        JButton addCurrent = button("Add current position", false, event -> appendCurrentFen());
        JComponent empty = emptyState("No positions yet",
                "Paste one per line, add the current board, or import a PGN.", addCurrent);
        empty.addMouseListener(new java.awt.event.MouseAdapter() {
            /**
             * Opens the editor when the empty state is clicked.
             *
             * @param event mouse press event
             */
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                showEditor();
                input.requestFocusInWindow();
            }
        });
        inputCard.add(empty, "empty");
        input.addFocusListener(new java.awt.event.FocusAdapter() {
            /**
             * Refreshes the input card on focus entry.
             *
             * @param event focus event
             */
            @Override
            public void focusGained(java.awt.event.FocusEvent event) {
                updateCard();
            }

            /**
             * Refreshes the input card on focus exit.
             *
             * @param event focus event
             */
            @Override
            public void focusLost(java.awt.event.FocusEvent event) {
                updateCard();
            }
        });
        JPanel cardHolder = transparentPanel(new BorderLayout());
        cardHolder.add(inputCard, BorderLayout.CENTER);
        cardHolder.setPreferredSize(new Dimension(10, EDITOR_HEIGHT));
        cardHolder.setMaximumSize(new Dimension(Integer.MAX_VALUE, EDITOR_HEIGHT));
        component.add(cardHolder, BorderLayout.CENTER);

        addFenButton = button("Add FEN", false, event -> appendCurrentFen());
        randomButton = button("Add random", false, event -> appendRandomPosition());
        importButton = button("Import PGN(s)", false, event -> importPgn());
        JButton clear = button("Clear", false, event -> input.setText(""));
        component.add(controlRow(FlowLayout.LEFT, addFenButton, randomButton, importButton, clear),
                BorderLayout.SOUTH);

        applyMode();
        updateCard();
        requestStatusUpdate();
    }

    /**
     * Returns the root component.
     *
     * @return editor component
     */
    public JComponent component() {
        return component;
    }

    /**
     * Returns the current positions text.
     *
     * @return positions text
     */
    public String text() {
        return input.getText();
    }

    /**
     * Replaces the positions text.
     *
     * @param value new text
     */
    public void setText(String value) {
        input.setText(value == null ? "" : value);
    }

    /**
     * Sets the input mode (FEN list or command script).
     *
     * @param newMode input mode
     */
    public void setMode(BatchInputKind newMode) {
        mode = newMode == null ? BatchInputKind.FEN_LINES : newMode;
        applyMode();
        requestStatusUpdate();
    }

    /**
     * Sets the listener invoked whenever the positions text changes.
     *
     * @param listener change callback
     */
    public void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> {
            // no-op
        } : listener;
    }

    /**
     * Returns whether the list holds at least one valid, error-free row.
     *
     * @return true when ready to run
     */
    public boolean isReady() {
        if (mode == BatchInputKind.COMMAND_LINES) {
            CommandScriptSummary scan = scanCommandScript();
            return scan.commands() > 0 && !scan.hasError();
        }
        FenInput.Summary scan = FenInput.validateBatchFenInput(input.getText());
        return scan.rows() > 0 && !scan.hasError();
    }

    /**
     * Appends one FEN line, ensuring it starts on a fresh row.
     *
     * @param fen FEN to append
     */
    public void appendFen(String fen) {
        if (fen == null || fen.isBlank()) {
            return;
        }
        String text = input.getText();
        if (!text.isEmpty() && !text.endsWith("\n") && !text.endsWith("\r")) {
            input.append(System.lineSeparator());
        }
        input.append(fen.trim() + System.lineSeparator());
        showEditor();
        input.requestFocusInWindow();
    }

    /**
     * Appends the current board FEN.
     */
    private void appendCurrentFen() {
        appendFen(context.currentFen());
    }

    /**
     * Appends a random reachable legal position.
     */
    private void appendRandomPosition() {
        appendFen(Setup.getRandomPositions(1, false).get(0).toString());
    }

    /**
     * Imports positions from one or more PGN files via a selection dialog.
     */
    private void importPgn() {
        JFileChooser chooser = FileDialogs.createFileChooser("Import PGN",
                null, new FileNameExtensionFilter("PGN, text, or FEN files", "pgn", "txt", "fen"));
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(context.dialogParent()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        List<Game> games = new ArrayList<>();
        for (java.io.File file : chooser.getSelectedFiles()) {
            try {
                games.addAll(Pgn.parseGames(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException ex) {
                context.showError("Import PGN failed", file.getName() + ": " + ex.getMessage());
            }
        }
        if (games.isEmpty()) {
            context.showError("Import PGN", "No games found in the selected file(s).");
            return;
        }
        List<String> fens = promptImportSelection(games);
        if (fens == null || fens.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder(input.getText());
        if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
            text.append(System.lineSeparator());
        }
        for (String fen : fens) {
            text.append(fen).append(System.lineSeparator());
        }
        input.setText(text.toString());
        showEditor();
    }

    /**
     * Shows the import-selection dialog and returns the chosen FENs, or null
     * when cancelled.
     *
     * @param games parsed games
     * @return selected FENs, or null
     */
    private List<String> promptImportSelection(List<Game> games) {
        java.awt.Window owner = SwingUtilities.getWindowAncestor(context.dialogParent());
        JDialog dialog = new JDialog(owner, "Import " + games.size()
                + (games.size() == 1 ? " game" : " games"), JDialog.ModalityType.APPLICATION_MODAL);
        JPanel body = new JPanel(new BorderLayout(Theme.SPACE_MD, Theme.SPACE_MD));
        body.setBackground(Theme.PANEL_SOLID);
        body.setBorder(Theme.pad(Theme.SPACE_LG));

        JRadioButton all = new JRadioButton("Every position (one FEN per ply)");
        JRadioButton last = new JRadioButton("Final position per game", true);
        JRadioButton nth = new JRadioButton("Every Nth ply");
        ButtonGroup group = new ButtonGroup();
        group.add(all);
        group.add(last);
        group.add(nth);
        SpinnerNumberModel strideModel = new SpinnerNumberModel(10, 1, 999, 1);
        JSpinner stride = new JSpinner(strideModel);
        JLabel count = new JLabel();
        Theme.foreground(count, Theme.ForegroundRole.MUTED);

        JPanel choices = new JPanel(new GridLayout(0, 1, 0, Theme.SPACE_XS));
        choices.setOpaque(false);
        for (JRadioButton radio : List.of(all, last, nth)) {
            radio.setOpaque(false);
            Theme.foreground(radio, Theme.ForegroundRole.TEXT);
        }
        choices.add(all);
        choices.add(last);
        JPanel nthRow = transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        nthRow.add(nth);
        nthRow.add(stride);
        choices.add(nthRow);
        body.add(choices, BorderLayout.NORTH);
        body.add(count, BorderLayout.CENTER);

        Runnable recount = () -> {
            GameModel.FenMode chosen = all.isSelected() ? GameModel.FenMode.ALL
                    : nth.isSelected() ? GameModel.FenMode.EVERY_NTH : GameModel.FenMode.FINAL;
            int total = 0;
            for (Game game : games) {
                total += GameModel.mainlineFens(game, chosen, (Integer) stride.getValue()).size();
            }
            count.setText("≈ " + total + " FEN" + (total == 1 ? "" : "s") + " from "
                    + games.size() + (games.size() == 1 ? " game" : " games"));
        };
        all.addActionListener(event -> recount.run());
        last.addActionListener(event -> recount.run());
        nth.addActionListener(event -> recount.run());
        stride.addChangeListener(event -> recount.run());
        recount.run();

        List<String> result = new ArrayList<>();
        boolean[] confirmed = { false };
        JButton importBtn = button("Import", true, event -> {
            GameModel.FenMode chosen = all.isSelected() ? GameModel.FenMode.ALL
                    : nth.isSelected() ? GameModel.FenMode.EVERY_NTH : GameModel.FenMode.FINAL;
            for (Game game : games) {
                result.addAll(GameModel.mainlineFens(game, chosen, (Integer) stride.getValue()));
            }
            confirmed[0] = true;
            dialog.dispose();
        });
        JButton cancel = button("Cancel", false, event -> dialog.dispose());
        body.add(controlRow(FlowLayout.RIGHT, importBtn, cancel), BorderLayout.SOUTH);

        dialog.setContentPane(body);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return confirmed[0] ? result : null;
    }

    /**
     * Applies mode-specific placeholder, tooltip, and button state.
     */
    private void applyMode() {
        boolean fen = mode == BatchInputKind.FEN_LINES;
        addFenButton.setEnabled(fen);
        randomButton.setEnabled(fen);
        importButton.setEnabled(fen);
        if (fen) {
            placeholder(input, "one FEN per line");
            input.setToolTipText("One FEN per line.");
        } else {
            placeholder(input, "one command per line");
            input.setToolTipText("One CRTK command per line; leading 'crtk' is optional.");
        }
    }

    /**
     * Handles a text edit: refresh the card, status, and notify listeners.
     */
    private void onInputChanged() {
        updateCard();
        requestStatusUpdate();
        changeListener.run();
    }

    /**
     * Shows the editable card.
     */
    private void showEditor() {
        ((CardLayout) inputCard.getLayout()).show(inputCard, "input");
    }

    /**
     * Shows the empty state until the input has content or focus.
     */
    private void updateCard() {
        boolean blank = input.getText() == null || input.getText().isBlank();
        boolean editing = input.isFocusOwner();
        ((CardLayout) inputCard.getLayout()).show(inputCard, blank && !editing ? "empty" : "input");
    }

    /**
     * Queues a coalesced status refresh.
     */
    private void requestStatusUpdate() {
        if (statusUpdateQueued) {
            return;
        }
        statusUpdateQueued = true;
        SwingUtilities.invokeLater(() -> {
            if (statusUpdateQueued) {
                refreshStatus();
            }
        });
    }

    /**
     * Refreshes the validity summary line.
     */
    private void refreshStatus() {
        statusUpdateQueued = false;
        if (mode == BatchInputKind.COMMAND_LINES) {
            CommandScriptSummary scan = scanCommandScript();
            if (scan.commands() == 0) {
                setStatus("No commands", Theme.ForegroundRole.MUTED);
            } else if (scan.hasError()) {
                setStatus(scan.commands() + " command" + (scan.commands() == 1 ? "" : "s")
                        + ", issue on line " + scan.firstErrorLine(), Theme.ForegroundRole.WARNING);
            } else {
                setStatus(scan.commands() + " command" + (scan.commands() == 1 ? "" : "s"),
                        Theme.ForegroundRole.MUTED);
            }
            return;
        }
        FenInput.Summary scan = FenInput.validateBatchFenInput(input.getText());
        if (scan.rows() == 0) {
            setStatus("No FEN rows", Theme.ForegroundRole.MUTED);
        } else if (scan.hasError()) {
            setStatus(scan.rows() + " row" + (scan.rows() == 1 ? "" : "s") + ", issue on line "
                    + scan.firstErrorLine(), Theme.ForegroundRole.WARNING);
        } else {
            setStatus(scan.validRows() + " FEN row" + (scan.validRows() == 1 ? "" : "s"),
                    Theme.ForegroundRole.MUTED);
        }
    }

    /**
     * Sets the status text and colour role.
     *
     * @param text status text
     * @param role foreground role
     */
    private void setStatus(String text, Theme.ForegroundRole role) {
        status.setText(text);
        Theme.foreground(status, role);
    }

    /**
     * Scans the input as command-script rows.
     *
     * @return command-script summary
     */
    CommandScriptSummary scanCommandScript() {
        String[] lines = input.getText().split("\\R", -1);
        int commands = 0;
        for (int i = 0; i < lines.length; i++) {
            String row = lines[i].trim();
            if (row.isEmpty() || row.startsWith("#")) {
                continue;
            }
            try {
                List<String> tokens = CommandLine.split(row);
                int commandIndex = !tokens.isEmpty() && "crtk".equals(tokens.get(0)) ? 1 : 0;
                if (commandIndex >= tokens.size() || tokens.get(commandIndex).isBlank()) {
                    return new CommandScriptSummary(commands, i + 1, "missing command after crtk");
                }
                commands++;
            } catch (IllegalArgumentException ex) {
                return new CommandScriptSummary(commands, i + 1, ex.getMessage());
            }
        }
        return new CommandScriptSummary(commands, 0, "");
    }

    /**
     * Summary of command-script input validation.
     *
     * @param commands non-comment command rows
     * @param firstErrorLine first invalid line, or zero
     * @param firstError first validation error
     */
    record CommandScriptSummary(int commands, int firstErrorLine, String firstError) {

        /**
         * Returns whether validation found an error.
         *
         * @return true when an error exists
         */
        boolean hasError() {
            return firstErrorLine > 0;
        }
    }
}
