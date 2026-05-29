package application.gui.workbench.game;

import application.cli.PathOps;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import chess.core.Position;
import chess.describe.ClassicalPositionDescriptionGenerator;
import chess.describe.PositionDescriptionDetail;
import chess.describe.PositionDescriptionInput;
import chess.describe.T5PositionDescriptionGenerator;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/**
 * Workbench panel for deterministic position descriptions.
 */
public final class PositionDescriptionPanel extends JPanel {

    /**
     * Serialization identifier for Swing component persistence.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Editable FEN source field.
     */
    private final JTextField fenField = new JTextField();

    /**
     * Engine selector.
     */
    private final JComboBox<String> engineBox = new JComboBox<>(new String[] { "classical", "t5" });

    /**
     * Detail-level selector.
     */
    private final JComboBox<String> detailBox = new JComboBox<>(new String[] { "brief", "normal", "full" });

    /**
     * Toggle that stops board-follow updates from replacing the current FEN.
     */
    private final JCheckBox freezeBox = Ui.withTooltip(new ToggleBox("Freeze", false),
            "Keep the current described position");

    /**
     * Generated description output.
     */
    private final JTextArea outputArea = new JTextArea();

    /**
     * Extracted source-signal output.
     */
    private final JTextArea signalsArea = new JTextArea();

    /**
     * Compact generation status label.
     */
    private final JLabel statusLabel = Ui.label("Idle");

    /**
     * Active background generation worker.
     */
    private SwingWorker<Result, Void> worker;

    /**
     * Monotonic request id used to discard stale worker results.
     */
    private long requestId;

    /**
     * Creates the panel.
     */
    public PositionDescriptionPanel() {
        super(new BorderLayout(Theme.SPACE_MD, Theme.SPACE_MD));
        setOpaque(true);
        setBackground(Theme.BG);
        setBorder(Theme.pad(12, 12, 12, 12));
        buildUi();
    }

    /**
     * Applies the current board FEN unless the panel is frozen.
     *
     * @param fen board FEN
     */
    public void setFen(String fen) {
        if (fen == null || fen.isBlank()) {
            return;
        }
        if (freezeBox.isSelected()) {
            statusLabel.setText("Frozen");
            return;
        }
        String trimmed = fen.trim();
        if (trimmed.equals(fenField.getText().trim()) && !outputArea.getText().isBlank()) {
            return;
        }
        fenField.setText(trimmed);
        regenerate();
    }

    /**
     * Builds and wires the Swing controls.
     */
    private void buildUi() {
        Theme.field(fenField);
        Theme.area(outputArea);
        Theme.area(signalsArea);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setEditable(false);
        signalsArea.setLineWrap(true);
        signalsArea.setWrapStyleWord(true);
        signalsArea.setEditable(false);
        detailBox.setSelectedItem("normal");
        Ui.styleCombos(engineBox, detailBox);

        JButton regenerate = Ui.button("Regenerate", false, event -> regenerate());
        JButton copy = Ui.button("Copy", false, event -> copyOutput());
        JButton export = Ui.button("Export", false, event -> exportOutput());
        engineBox.addActionListener(event -> regenerate());
        detailBox.addActionListener(event -> regenerate());
        freezeBox.addActionListener(event -> statusLabel.setText(freezeBox.isSelected() ? "Frozen" : "Following"));

        JPanel toolbar = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        toolbar.add(Ui.labeledControl("Engine", engineBox));
        toolbar.add(Ui.labeledControl("Detail", detailBox));
        toolbar.add(freezeBox);
        toolbar.add(regenerate);
        toolbar.add(copy);
        toolbar.add(export);
        toolbar.add(statusLabel);

        JPanel north = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, Theme.SPACE_SM));
        north.add(toolbar, BorderLayout.NORTH);
        north.add(fenField, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                Ui.titled("Description", Ui.scroll(outputArea)),
                Ui.titled("Signals", Ui.scroll(signalsArea)));
        split.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        split.setResizeWeight(0.64);
        split.setDividerLocation(0.64);
        split.setContinuousLayout(true);
        SplitPaneStyler.style(split);

        add(north, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    /**
     * Regenerates description text for the current FEN and selected options.
     */
    private void regenerate() {
        String fen = fenField.getText().trim();
        if (fen.isBlank()) {
            statusLabel.setText("No position");
            return;
        }
        if ("t5".equals(engineBox.getSelectedItem())) {
            T5PositionDescriptionGenerator t5 = new T5PositionDescriptionGenerator(
                    Path.of(application.Config.getT5ModelPath()), 128);
            outputArea.setText("");
            signalsArea.setText("engine=t5\nstatus=unavailable\nmodel=" + t5.modelPath());
            statusLabel.setText("T5 unavailable");
            return;
        }
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        long id = ++requestId;
        statusLabel.setText("Generating");
        worker = new SwingWorker<>() {
            /**
             * Generates description text away from the event-dispatch thread.
             *
             * @return generation result
             */
            @Override
            protected Result doInBackground() {
                Position position = new Position(fen);
                PositionDescriptionInput input = PositionDescriptionInput.from(position);
                PositionDescriptionDetail detail = PositionDescriptionDetail.parse(String.valueOf(detailBox.getSelectedItem()));
                String text = new ClassicalPositionDescriptionGenerator().generate(input, detail);
                return new Result(input, text);
            }

            /**
             * Applies the completed generation result on the event-dispatch thread.
             */
            @Override
            protected void done() {
                if (isCancelled() || id != requestId) {
                    return;
                }
                try {
                    Result result = get();
                    outputArea.setText(result.text());
                    signalsArea.setText(signalText(result.input()));
                    statusLabel.setText("Classical");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Interrupted");
                } catch (ExecutionException ex) {
                    outputArea.setText("");
                    signalsArea.setText("");
                    statusLabel.setText(errorText(ex.getCause()));
                }
            }
        };
        worker.execute();
    }

    /**
     * Formats the structured source signals shown beside the description.
     *
     * @param input extracted description input
     * @return signal text
     */
    private static String signalText(PositionDescriptionInput input) {
        return "fen=" + input.fen() + "\n"
                + "side=" + input.sideToMove() + "\n"
                + "phase=" + input.phase() + "\n"
                + "status=" + input.status() + "\n"
                + "legal_moves=" + input.moves().legal() + "\n"
                + "forcing_moves=" + input.moves().forcing() + "\n"
                + "material_balance_cp=" + input.material().balanceCp() + "\n"
                + "eval_cp_white=" + input.evaluation().cpWhite() + "\n"
                + "tags=" + input.tags().size() + "\n"
                + "candidates=" + input.candidates().size();
    }

    /**
     * Copies the current generated description to the clipboard.
     */
    private void copyOutput() {
        String text = outputArea.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        statusLabel.setText("Copied");
    }

    /**
     * Exports the current generated description to the dump directory.
     */
    private void exportOutput() {
        String text = outputArea.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        Path output = PathOps.dumpPath("position-description.txt");
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, text + System.lineSeparator(), StandardCharsets.UTF_8);
            statusLabel.setText("Exported " + output);
        } catch (IOException ex) {
            statusLabel.setText(errorText(ex));
        }
    }

    /**
     * Extracts a short status message from a failure.
     *
     * @param failure failure, possibly null
     * @return short status text
     */
    private static String errorText(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        if (message == null || message.isBlank()) {
            return "Failed";
        }
        return message.length() > 42 ? message.substring(0, 42).toLowerCase(Locale.ROOT) : message;
    }

    /**
     * Background generation result.
     *
     * @param input extracted source signals
     * @param text generated description text
     */
    private record Result(PositionDescriptionInput input, String text) {
    }
}
