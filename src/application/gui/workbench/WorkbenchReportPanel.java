package application.gui.workbench;

import static application.gui.workbench.WorkbenchUi.button;
import static application.gui.workbench.WorkbenchUi.buttonRow;
import static application.gui.workbench.WorkbenchUi.label;
import static application.gui.workbench.WorkbenchUi.placeholder;
import static application.gui.workbench.WorkbenchUi.scroll;
import static application.gui.workbench.WorkbenchUi.styleAreas;
import static application.gui.workbench.WorkbenchUi.styleFields;
import static application.gui.workbench.WorkbenchUi.trimmed;
import static application.gui.workbench.WorkbenchUi.transparentPanel;
import static application.gui.workbench.WorkbenchSwingTasks.runAsync;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;

/**
 * Position and game-line report builder for the publishing workflow.
 */
final class WorkbenchReportPanel {

    /**
     * Services supplied by the frame.
     */
    interface Host {

        /**
         * Returns the owning component for dialogs.
         *
         * @return owner component
         */
        Component owner();

        /**
         * Returns the current board position.
         *
         * @return current position
         */
        Position currentPosition();

        /**
         * Returns the currently visible legal move array.
         *
         * @return legal moves
         */
        short[] visibleMoves();

        /**
         * Returns the game model.
         *
         * @return game model
         */
        WorkbenchGameModel gameModel();

        /**
         * Returns the active tag model.
         *
         * @return tag model
         */
        DefaultListModel<String> tagModel();

        /**
         * Copies text to the clipboard.
         *
         * @param text text
         */
        void copyText(String text);

        /**
         * Appends text to the console.
         *
         * @param text text
         */
        void appendConsole(String text);

        /**
         * Shows a toast.
         *
         * @param kind toast kind
         * @param message message
         */
        void toast(WorkbenchToast.Kind kind, String message);

        /**
         * Shows an error.
         *
         * @param title title
         * @param message message
         */
        void showError(String title, String message);
    }

    /**
     * Host callbacks.
     */
    private final Host host;

    /**
     * Root component.
     */
    private final JComponent component;

    /**
     * Optional report note.
     */
    private final JTextField noteField = new JTextField();

    /**
     * Plain-text report preview.
     */
    private final JTextArea preview = new JTextArea();

    /**
     * Creates the report panel.
     *
     * @param host host callbacks
     */
    WorkbenchReportPanel(Host host) {
        this.host = host;
        this.component = createComponent();
    }

    /**
     * Returns the root component.
     *
     * @return root component
     */
    JComponent component() {
        return component;
    }

    /**
     * Generates a report for the current position and game line.
     */
    void generateReport() {
        preview.setText(buildReportText());
        preview.setCaretPosition(0);
    }

    /**
     * Copies the current report, generating it first when empty.
     */
    void copyReport() {
        if (preview.getText() == null || preview.getText().isBlank()) {
            generateReport();
        }
        host.copyText(preview.getText());
    }

    /**
     * Saves the current report to a text file.
     */
    void saveReportFile() {
        if (preview.getText() == null || preview.getText().isBlank()) {
            generateReport();
        }
        JFileChooser chooser = WorkbenchFileDialogs.createFileChooser(null, new File("workbench-report.txt"),
                new FileNameExtensionFilter("Text report", "txt"));
        int result = chooser.showSaveDialog(host.owner());
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = WorkbenchFileDialogs.ensureExtension(chooser.getSelectedFile(), ".txt");
        String contents = preview.getText();
        runAsync(
                () -> {
                    Files.writeString(file.toPath(), contents, StandardCharsets.UTF_8);
                    return file;
                },
                saved -> {
                    host.appendConsole("Saved report to " + saved + "\n");
                    host.toast(WorkbenchToast.Kind.SUCCESS, "Saved report to " + saved.getName());
                },
                ex -> host.showError("Save report failed", ex.getMessage()));
    }

    /**
     * Builds the panel UI.
     *
     * @return root component
     */
    private JComponent createComponent() {
        JPanel panel = new WorkbenchSurfacePanel(new BorderLayout(8, 8));
        JPanel header = transparentPanel(new BorderLayout(0, 2));
        header.add(WorkbenchTheme.section("Report"), BorderLayout.NORTH);
        header.add(WorkbenchUi.caption(
                "A plain-text analysis of the current position and game line — generate, then copy or save it."),
                BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);

        styleFields(noteField);
        placeholder(noteField, "e.g. key idea, theme, or next action");
        noteField.setToolTipText("Optional note included in the generated report.");
        JPanel body = transparentPanel(new BorderLayout(8, 8));
        JPanel noteRow = transparentPanel(new BorderLayout(8, 0));
        noteRow.add(label("note"), BorderLayout.WEST);
        noteRow.add(noteField, BorderLayout.CENTER);
        body.add(noteRow, BorderLayout.NORTH);

        styleAreas(preview);
        preview.setRows(20);
        preview.setLineWrap(false);
        preview.setWrapStyleWord(false);
        preview.setEditable(false);
        JPanel reportBox = transparentPanel(new BorderLayout(8, 0));
        JLabel reportLabel = label("report");
        reportLabel.setVerticalAlignment(SwingConstants.TOP);
        reportLabel.setBorder(WorkbenchTheme.pad(8, 0, 0, 0));
        reportBox.add(reportLabel, BorderLayout.WEST);
        reportBox.add(scroll(preview), BorderLayout.CENTER);
        body.add(reportBox, BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);

        panel.add(buttonRow(FlowLayout.LEFT,
                button("Generate Report", true, event -> generateReport()),
                button("Copy Report", false, event -> copyReport()),
                button("Save Report", false, event -> saveReportFile())), BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Builds the current report text.
     *
     * @return report text
     */
    private String buildReportText() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder(2048);
        sb.append("ChessRTK Workbench Report").append(newline);
        sb.append("========================").append(newline).append(newline);
        Position currentPosition = host.currentPosition();
        if (currentPosition == null) {
            sb.append("No position loaded.").append(newline);
            return sb.toString();
        }
        sb.append("Position").append(newline);
        sb.append("FEN: ").append(currentPosition).append(newline);
        sb.append("Side: ").append(currentPosition.isWhiteToMove() ? "White" : "Black").append(" to move")
                .append(newline);
        sb.append("Status: ").append(WorkbenchPositionText.status(currentPosition)).append(newline);
        sb.append("Legal moves: ").append(host.visibleMoves().length).append(newline);
        sb.append("Tags: ").append(formatCurrentTags()).append(newline).append(newline);

        appendLegalMoves(sb, currentPosition);
        appendGameReport(sb);
        String note = trimmed(noteField);
        if (!note.isEmpty()) {
            sb.append("Note").append(newline);
            sb.append(note).append(newline);
        }
        return sb.toString();
    }

    /**
     * Appends legal move details to a report.
     *
     * @param sb target builder
     * @param currentPosition current position
     */
    private static void appendLegalMoves(StringBuilder sb, Position currentPosition) {
        String newline = System.lineSeparator();
        sb.append("Legal Move Table").append(newline);
        MoveList moves = currentPosition.legalMoves();
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            sb.append(String.format(Locale.ROOT, "%2d. %-8s %s", i + 1,
                    WorkbenchPositionText.safeSan(currentPosition, move), Move.toString(move))).append(newline);
        }
        sb.append(newline);
    }

    /**
     * Appends game-line details to a report.
     *
     * @param sb target builder
     */
    private void appendGameReport(StringBuilder sb) {
        String newline = System.lineSeparator();
        WorkbenchGameModel gameModel = host.gameModel();
        sb.append("Game Line").append(newline);
        sb.append("Current ply: ").append(gameModel.currentPly()).append(" / ").append(gameModel.lastPly())
                .append(newline);
        if (gameModel.lastPly() <= 0) {
            sb.append("(no moves)").append(newline).append(newline);
            return;
        }
        sb.append("SAN: ").append(gameModel.sanLine()).append(newline);
        sb.append("UCI: ").append(gameModel.uciLine()).append(newline);
        sb.append("PGN:").append(newline).append(gameModel.pgn()).append(newline);
        sb.append("FEN list:").append(newline).append(gameModel.fenList()).append(newline).append(newline);
    }

    /**
     * Formats the current tag list for reports.
     *
     * @return formatted tags
     */
    private String formatCurrentTags() {
        List<String> tags = new ArrayList<>();
        DefaultListModel<String> tagModel = host.tagModel();
        for (int i = 0; i < tagModel.size(); i++) {
            String tag = tagModel.get(i);
            if (tag != null && !tag.isBlank() && !"calculating...".equals(tag)
                    && !tag.startsWith("tagging failed:")) {
                tags.add(tag);
            }
        }
        return tags.isEmpty() ? "(none)" : String.join(", ", tags);
    }

}
