package application.gui.workbench.game;

import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import chess.core.Piece;
import chess.core.Position;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * Endgame tablebase status and analysis panel for the current board position.
 */
public final class TablebasePanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Standard Syzygy piece limit.
     */
    private static final int TABLEBASE_PIECE_LIMIT = 7;

    /**
     * Fixed default node budget for an endgame truth probe.
     */
    private static final String DEFAULT_NODES = "100000";

    /**
     * Tablebase hit line pattern emitted by {@code engine analyze}.
     */
    private static final Pattern TBHITS_PATTERN =
            Pattern.compile("(?m)^\\s*tablebase hits:\\s*([0-9_,]+)\\b");

    /**
     * Best move line pattern emitted by {@code engine analyze}.
     */
    private static final Pattern BEST_PATTERN =
            Pattern.compile("(?m)^\\s*best:\\s*(\\S+)\\s*\\(([^)]*)\\)");

    /**
     * PV header pattern.
     */
    private static final Pattern PV_PATTERN = Pattern.compile("(?m)^PV\\d+\\b");

    /**
     * Current FEN supplier.
     */
    private final transient Supplier<String> fenSupplier;

    /**
     * Clipboard helper.
     */
    private final transient Consumer<String> copyText;

    /**
     * State labels.
     */
    private final JLabel piecesLabel = Ui.label("");
    /**
     * Label showing the current status.
     */
    private final JLabel statusLabel = Ui.label("");
    /**
     * Label showing hits.
     */
    private final JLabel hitsLabel = Ui.label("");
    /**
     * Label showing best.
     */
    private final JLabel bestLabel = Ui.label("");

    /**
     * Raw command output.
     */
    private final JTextArea outputArea = new JTextArea();

    /**
     * Active command handle.
     */
    private CommandRunner.RunningCommand running;

    /**
     * Creates a tablebase panel.
     *
     * @param fenSupplier current FEN supplier
     * @param copyText clipboard helper
     */
    public TablebasePanel(Supplier<String> fenSupplier, Consumer<String> copyText) {
        super(new BorderLayout(Theme.SPACE_SM, Theme.SPACE_SM));
        this.fenSupplier = fenSupplier == null ? () -> "" : fenSupplier;
        this.copyText = copyText == null ? text -> { } : copyText;
        configure();
        refreshPosition();
    }

    /**
     * Builds the fixed-node endgame analysis command.
     *
     * @param fen target FEN
     * @return command args
     */
    public static List<String> analyzeArgs(String fen) {
        List<String> args = new ArrayList<>();
        args.add("engine");
        args.add("analyze");
        args.add("--fen");
        args.add(fen == null ? "" : fen);
        args.add("--multipv");
        args.add("8");
        args.add("--max-nodes");
        args.add(DEFAULT_NODES);
        args.add("--wdl");
        return List.copyOf(args);
    }

    /**
     * Summarizes a command output block for tablebase status.
     *
     * @param fen analyzed FEN
     * @param output command output
     * @return parsed summary
     */
    public static Summary summarize(String fen, String output) {
        int pieces = pieceCount(fen);
        long hits = parseHits(output);
        String best = parseBest(output);
        int pvs = countMatches(PV_PATTERN, output);
        boolean eligible = pieces > 0 && pieces <= TABLEBASE_PIECE_LIMIT;
        String verdict;
        if (!eligible) {
            verdict = "Not tablebase-eligible";
        } else if (hits > 0) {
            verdict = "Tablebase hit reported";
        } else {
            verdict = "No tablebase hit reported";
        }
        return new Summary(pieces, eligible, hits, pvs, best, verdict);
    }

    /**
     * Refreshes the current position status.
     */
    public void refreshPosition() {
        Summary summary = summarize(fenSupplier.get(), outputArea.getText());
        applySummary(summary);
    }

    /**
     * Configures the panel.
     */
    private void configure() {
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setBorder(Theme.pad(Theme.SPACE_MD));
        getAccessibleContext().setAccessibleName("Endgame tablebase panel");
        getAccessibleContext().setAccessibleDescription(
                "Shows piece count, tablebase eligibility, tablebase hits, and engine endgame output.");
        styleAreas(outputArea);
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.getAccessibleContext().setAccessibleName("Endgame engine output");
        outputArea.getAccessibleContext().setAccessibleDescription(
                "Output from the fixed-node endgame analysis command.");
        add(createToolbar(), BorderLayout.NORTH);
        add(createSummary(), BorderLayout.CENTER);
        add(Ui.titled("Engine Output", scroll(outputArea)), BorderLayout.SOUTH);
    }

    /**
     * Creates action toolbar.
     *
     * @return toolbar
     */
    private JComponent createToolbar() {
        JPanel row = transparentPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        row.add(Ui.button("Analyze Endgame", true, event -> runAnalysis()));
        row.add(Ui.button("Copy Command", false, event -> copyCommand()));
        row.add(Ui.button("Copy Output", false, event -> copyText.accept(outputArea.getText())));
        return row;
    }

    /**
     * Creates the summary block.
     *
     * @return summary component
     */
    private JComponent createSummary() {
        JPanel panel = new SurfacePanel(new java.awt.GridLayout(0, 1, 0, Theme.SPACE_XS));
        panel.add(piecesLabel);
        panel.add(statusLabel);
        panel.add(hitsLabel);
        panel.add(bestLabel);
        return panel;
    }

    /**
     * Starts a fixed-node endgame analysis command.
     */
    private void runAnalysis() {
        if (running != null && running.isRunning()) {
            return;
        }
        String fen = fenSupplier.get();
        List<String> args = analyzeArgs(fen);
        outputArea.setText("$ " + CommandRunner.displayCommand(args) + System.lineSeparator());
        statusLabel.setText("Analyzing endgame");
        running = CommandRunner.run(args, null, outputArea::append, result -> {
            Summary summary = summarize(fen, result.output());
            applySummary(summary);
        }, ex -> {
            statusLabel.setText("Analysis failed");
            outputArea.append(System.lineSeparator() + ex.getMessage() + System.lineSeparator());
        });
    }

    /**
     * Copies the command preview.
     */
    private void copyCommand() {
        copyText.accept(CommandRunner.displayCommand(analyzeArgs(fenSupplier.get())));
        statusLabel.setText("Command copied");
    }

    /**
     * Applies a parsed summary to labels.
     *
     * @param summary summary text
     */
    private void applySummary(Summary summary) {
        piecesLabel.setText("Pieces: " + summary.pieces()
                + (summary.eligible() ? " (Syzygy-eligible)" : " (above Syzygy limit)"));
        statusLabel.setText("Status: " + summary.verdict());
        hitsLabel.setText("Tablebase hits: " + summary.tablebaseHits() + "  |  PVs: " + summary.pvCount());
        bestLabel.setText(summary.bestMove().isBlank() ? "Best move: -" : "Best move: " + summary.bestMove());
    }

    /**
     * Counts pieces in a FEN.
     *
     * @param fen source FEN
     * @return piece count
     */
    private static int pieceCount(String fen) {
        try {
            Position position = new Position(fen);
            int count = 0;
            for (int square = 0; square < 64; square++) {
                if (position.pieceAt(square) != Piece.EMPTY) {
                    count++;
                }
            }
            return count;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /**
     * Parses tablebase-hit count from command output.
     *
     * @param output output text
     * @return hits
     */
    private static long parseHits(String output) {
        Matcher matcher = TBHITS_PATTERN.matcher(output == null ? "" : output);
        long hits = 0;
        while (matcher.find()) {
            try {
                hits += Long.parseLong(matcher.group(1).replace(",", "").replace("_", ""));
            } catch (NumberFormatException ex) {
                // Ignore malformed hit fragments.
            }
        }
        return hits;
    }

    /**
     * Parses the first best move line.
     *
     * @param output output text
     * @return compact best move
     */
    private static String parseBest(String output) {
        Matcher matcher = BEST_PATTERN.matcher(output == null ? "" : output);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1) + " (" + matcher.group(2) + ")";
    }

    /**
     * Counts pattern matches.
     *
     * @param pattern source pattern
     * @param text text to render or parse
     * @return match count
     */
    private static int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Parsed tablebase panel summary.
     *
     * @param pieces piece count
     * @param eligible true when piece count is within the tablebase limit
     * @param tablebaseHits parsed tablebase hits
     * @param pvCount principal variation count
     * @param bestMove best move text
     * @param verdict status text
     */
    public record Summary(
            int pieces,
            boolean eligible,
            long tablebaseHits,
            int pvCount,
            String bestMove,
            String verdict) {
    }
}
