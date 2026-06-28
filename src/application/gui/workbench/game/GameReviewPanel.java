package application.gui.workbench.game;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.NotationPainter;
import application.gui.workbench.ui.SegmentedSwitcher;
import chess.core.Move;
import chess.core.Position;
import chess.describe.ClassicalPositionDescriptionGenerator;
import chess.describe.PositionDescriptionDetail;
import chess.describe.PositionDescriptionInput;
import chess.eval.Classical;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.tag.MoveEffect;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;

/**
 * Post-game review panel for the current Workbench game line.
 */
public final class GameReviewPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Centipawn loss threshold for a blunder.
     */
    private static final int BLUNDER_CP = 300;

    /**
     * Centipawn loss threshold for a mistake.
     */
    private static final int MISTAKE_CP = 150;

    /**
     * Centipawn loss threshold for an inaccuracy.
     */
    private static final int INACCURACY_CP = 75;

    /**
     * Review mode card identifier.
     */
    private static final String CARD_ANALYSIS = "analysis";

    /**
     * Move-times card identifier.
     */
    private static final String CARD_TIMES = "times";

    /**
     * Crosstable card identifier.
     */
    private static final String CARD_CROSSTABLE = "crosstable";

    /**
     * Share/export card identifier.
     */
    private static final String CARD_EXPORT = "export";

    /**
     * Lichess-like review mode labels.
     */
    private static final String[] REVIEW_MODES = {
            "Computer analysis",
            "Move times",
            "Crosstable",
            "Share & export"
    };

    /**
     * Supplies the current game model.
     */
    private final transient Supplier<GameModel> gameSupplier;

    /**
     * Jumps the shared board to a requested mainline ply.
     */
    private final transient IntConsumer jumpToPly;

    /**
     * Copies text through the Workbench clipboard helper.
     */
    private final transient Consumer<String> copyText;

    /**
     * Produces CLI-backed review artifacts for the current game.
     */
    private final transient ReviewArtifactProducer artifactProducer;

    /**
     * Review table model.
     */
    private final ReviewTableModel tableModel = new ReviewTableModel();

    /**
     * Review result table.
     */
    private final JTable table = new JTable(tableModel);

    /**
     * Detailed explanation for the selected review row.
     */
    private final JTextArea detailArea = Ui.commandBlock("");

    /**
     * PGN export text shown in the Share & export mode.
     */
    private final JTextArea exportArea = Ui.commandBlock("");

    /**
     * Compact review status.
     */
    private final JLabel statusLabel = Ui.label("Review not run");

    /**
     * Lichess-style mode switcher.
     */
    private final SegmentedSwitcher reviewTabs = new SegmentedSwitcher(REVIEW_MODES);

    /**
     * Card layout backing the review modes.
     */
    private final CardLayout reviewCards = new CardLayout();

    /**
     * Review mode cards.
     */
    private final JPanel reviewCardPanel = Ui.transparentPanel(reviewCards);

    /**
     * Lichess-like evaluation timeline.
     */
    private final EvaluationTimelinePanel evaluationTimeline = new EvaluationTimelinePanel();

    /**
     * Lichess-like move effort/times chart.
     */
    private final MoveTimesPanel moveTimesPanel = new MoveTimesPanel();

    /**
     * Lichess-like crosstable summary.
     */
    private final CrosstablePanel crosstablePanel = new CrosstablePanel();

    /**
     * Lichess-like notation and verdict lane.
     */
    private final ReviewNotationPanel notationPanel = new ReviewNotationPanel();

    /**
     * Active background worker.
     */
    private SwingWorker<List<ReviewFinding>, Void> worker;

    /**
     * Active artifact-production task.
     */
    private ReviewArtifactProducer.RunningTask artifactTask = ReviewArtifactProducer.RunningTask.NONE;

    /**
     * Creates a post-game review panel.
     *
     * @param gameSupplier current game supplier
     * @param jumpToPly mainline navigation callback
     * @param copyText clipboard callback
     */
    public GameReviewPanel(Supplier<GameModel> gameSupplier, IntConsumer jumpToPly, Consumer<String> copyText) {
        this(gameSupplier, jumpToPly, copyText, ReviewArtifactProducer.unavailable());
    }

    /**
     * Creates a post-game review panel.
     *
     * @param gameSupplier current game supplier
     * @param jumpToPly mainline navigation callback
     * @param copyText clipboard callback
     * @param artifactProducer review artifact producer
     */
    public GameReviewPanel(Supplier<GameModel> gameSupplier, IntConsumer jumpToPly, Consumer<String> copyText,
            ReviewArtifactProducer artifactProducer) {
        super(new BorderLayout(Theme.SPACE_SM, Theme.SPACE_SM));
        this.gameSupplier = Objects.requireNonNull(gameSupplier, "gameSupplier");
        this.jumpToPly = Objects.requireNonNull(jumpToPly, "jumpToPly");
        this.copyText = Objects.requireNonNull(copyText, "copyText");
        this.artifactProducer = Objects.requireNonNull(artifactProducer, "artifactProducer");
        configurePanel();
    }

    /**
     * Analyzes a game line into deterministic review findings.
     *
     * @param model game model
     * @return review findings
     */
    public static List<ReviewFinding> analyze(GameModel model) {
        if (model == null || model.lastPly() <= 0) {
            return List.of();
        }
        Classical evaluator = new Classical();
        ClassicalPositionDescriptionGenerator description = new ClassicalPositionDescriptionGenerator();
        List<ReviewFinding> findings = new ArrayList<>();
        for (GameModel.PlySnapshot snapshot : model.mainlineSnapshots()) {
            Position before = new Position(snapshot.beforeFen());
            Position after = new Position(snapshot.afterFen());
            int beforeWhite = whiteCentipawns(evaluator, before);
            int afterWhite = whiteCentipawns(evaluator, after);
            int moverGain = snapshot.whiteMove() ? afterWhite - beforeWhite : beforeWhite - afterWhite;
            int loss = Math.max(0, -moverGain);
            String verdict = verdict(loss, moverGain);
            List<String> effects = MoveEffect.effects(before, after);
            String effectSummary = summarizeEffects(effects);
            String positionText = description.generate(PositionDescriptionInput.from(after),
                    PositionDescriptionDetail.BRIEF);
            String summary = summary(snapshot, verdict, loss, beforeWhite, afterWhite, effectSummary,
                    positionText);
            findings.add(new ReviewFinding(snapshot.ply(), snapshot.san(), snapshot.uci(),
                    snapshot.beforeFen(), snapshot.afterFen(), verdict, loss, beforeWhite, afterWhite,
                    effectSummary, summary));
        }
        findings.sort((a, b) -> {
            int bySeverity = Integer.compare(severityRank(b.verdict()), severityRank(a.verdict()));
            return bySeverity != 0 ? bySeverity : Integer.compare(b.lossCp(), a.lossCp());
        });
        return List.copyOf(findings);
    }

    /**
     * Returns the current visible review row count.
     *
     * @return row count
     */
    public int rowCount() {
        return tableModel.getRowCount();
    }

    /**
     * Returns the Lichess-like review mode labels.
     *
     * @return review mode labels
     */
    public List<String> reviewModeLabels() {
        return List.of(REVIEW_MODES.clone());
    }

    /**
     * Selects a review mode by index.
     *
     * @param index mode index
     * @return true when selected
     */
    public boolean selectReviewMode(int index) {
        if (index < 0 || index >= REVIEW_MODES.length) {
            return false;
        }
        reviewTabs.setSelectedIndex(index);
        showReviewMode(index);
        return true;
    }

    /**
     * Returns the current export PGN text.
     *
     * @return PGN export text
     */
    public String exportText() {
        return exportArea.getText();
    }

    /**
     * Returns the notation text used by the Lichess-like review lane.
     *
     * @return notation text
     */
    public String notationText() {
        return notationPanel.notationText();
    }

    /**
     * Loads produced review artifacts into the panel without recomputing verdicts.
     *
     * @param reviewJsonl review JSONL path
     * @param studyJsonl optional study-unit JSONL path, or {@code null}
     * @throws IOException if an artifact cannot be read
     */
    public void loadReviewArtifacts(Path reviewJsonl, Path studyJsonl) throws IOException {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        if (artifactTask != null && artifactTask.isRunning()) {
            artifactTask.cancel();
        }
        List<ReviewFinding> findings = loadReviewArtifactRows(reviewJsonl, studyJsonl);
        tableModel.setRows(findings);
        refreshLichessViews();
        statusLabel.setText(statusText(findings));
        if (!findings.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        updateDetails();
    }

    /**
     * Loads produced review artifacts into table-ready rows.
     *
     * @param reviewJsonl review JSONL path
     * @param studyJsonl optional study-unit JSONL path, or {@code null}
     * @return review findings
     * @throws IOException if an artifact cannot be read
     */
    public static List<ReviewFinding> loadReviewArtifactRows(Path reviewJsonl, Path studyJsonl)
            throws IOException {
        return ReviewArtifactLoader.load(reviewJsonl, studyJsonl);
    }

    /**
     * Runs the CLI review-to-study path and loads the produced artifacts.
     *
     * @return true when artifact production was started
     */
    public boolean buildStudyArtifactsFromGame() {
        if (artifactTask != null && artifactTask.isRunning()) {
            statusLabel.setText("Review command already running");
            return false;
        }
        GameModel model = gameSupplier.get();
        if (model == null || model.lastPly() <= 0) {
            statusLabel.setText("No moves to review");
            return false;
        }
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        tableModel.setRows(List.of());
        detailArea.setText("");
        statusLabel.setText("Running review command");
        try {
            artifactTask = artifactProducer.produce(model.pgn(), this::appendArtifactOutput,
                    this::applyArtifactResult, this::showArtifactError);
            return true;
        } catch (IOException | IllegalArgumentException ex) {
            showArtifactError(ex);
            return false;
        }
    }

    /**
     * Runs review generation for the current game.
     */
    public void runReview() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        if (artifactTask != null && artifactTask.isRunning()) {
            artifactTask.cancel();
        }
        statusLabel.setText("Reviewing");
        detailArea.setText("");
        GameModel snapshot = gameSupplier.get();
        worker = new SwingWorker<>() {
            /**
             * Reviews the game away from the event-dispatch thread.
             *
             * @return review findings
             */
            @Override
            protected List<ReviewFinding> doInBackground() {
                return analyze(snapshot);
            }

            /**
             * Applies review results on the event-dispatch thread.
             */
            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    List<ReviewFinding> findings = get();
                    tableModel.setRows(findings);
                    refreshLichessViews();
                    statusLabel.setText(statusText(findings));
                    if (!findings.isEmpty()) {
                        table.setRowSelectionInterval(0, 0);
                    }
                    updateDetails();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Interrupted");
                } catch (java.util.concurrent.ExecutionException ex) {
                    tableModel.setRows(List.of());
                    refreshLichessViews();
                    statusLabel.setText("Review failed");
                    detailArea.setText(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * Selects the first visible review finding.
     *
     * @return true when selected
     */
    public boolean selectFirstRow() {
        if (tableModel.getRowCount() == 0) {
            return false;
        }
        table.setRowSelectionInterval(0, 0);
        updateDetails();
        return true;
    }

    /**
     * Jumps to the position before the selected move.
     *
     * @return true when navigation happened
     */
    public boolean retrySelected() {
        ReviewFinding finding = selectedFinding();
        if (finding == null) {
            statusLabel.setText("Select a review row");
            return false;
        }
        jumpToPly.accept(Math.max(0, finding.ply() - 1));
        statusLabel.setText("Retry " + finding.san());
        return true;
    }

    /**
     * Jumps to the position after the selected move.
     *
     * @return true when navigation happened
     */
    public boolean showSelectedMove() {
        ReviewFinding finding = selectedFinding();
        if (finding == null) {
            statusLabel.setText("Select a review row");
            return false;
        }
        jumpToPly.accept(finding.ply());
        statusLabel.setText("Showing " + finding.san());
        return true;
    }

    /**
     * Copies the selected review explanation.
     *
     * @return true when copied
     */
    public boolean copySelectedReview() {
        ReviewFinding finding = selectedFinding();
        if (finding == null) {
            statusLabel.setText("Select a review row");
            return false;
        }
        copyText.accept(finding.summary());
        statusLabel.setText("Copied review");
        return true;
    }

    /**
     * Builds and styles the panel.
     */
    private void configurePanel() {
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setBorder(Theme.pad(Theme.SPACE_MD));

        Theme.table(table, Theme.TABLE_ROW_HEIGHT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.getAccessibleContext().setAccessibleName("Post-game review findings");
        table.getAccessibleContext().setAccessibleDescription(
                "Moves ranked by deterministic static swing, tactical effects, and review verdict.");
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateDetails();
            }
        });

        Theme.area(detailArea);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.getAccessibleContext().setAccessibleName("Review explanation");
        detailArea.getAccessibleContext().setAccessibleDescription(
                "Detailed deterministic explanation for the selected review row.");

        exportArea.setEditable(false);
        exportArea.setLineWrap(true);
        exportArea.setWrapStyleWord(true);
        exportArea.getAccessibleContext().setAccessibleName("PGN export");
        exportArea.getAccessibleContext().setAccessibleDescription(
                "PGN text for the current reviewed game.");

        reviewTabs.getAccessibleContext().setAccessibleName("Review modes");
        reviewTabs.addActionListener(event -> showReviewMode(reviewTabs.getSelectedIndex()));
        reviewCardPanel.add(createAnalysisCard(), CARD_ANALYSIS);
        reviewCardPanel.add(createMoveTimesCard(), CARD_TIMES);
        reviewCardPanel.add(createCrosstableCard(), CARD_CROSSTABLE);
        reviewCardPanel.add(createExportCard(), CARD_EXPORT);

        JPanel body = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        body.add(reviewTabs, BorderLayout.NORTH);
        body.add(reviewCardPanel, BorderLayout.CENTER);

        add(createToolbar(), BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        refreshLichessViews();
    }

    /**
     * Creates the Computer analysis card.
     *
     * @return analysis card
     */
    private JComponent createAnalysisCard() {
        JPanel panel = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        evaluationTimeline.setPreferredSize(new Dimension(360, 150));
        detailArea.setRows(5);
        panel.add(evaluationTimeline, BorderLayout.NORTH);
        panel.add(Ui.scroll(notationPanel, () -> Theme.PANEL_SOLID), BorderLayout.CENTER);
        panel.add(Ui.titled("Move note", Ui.scroll(detailArea)), BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Creates the Move times card.
     *
     * @return move-times card
     */
    private JComponent createMoveTimesCard() {
        JPanel panel = Ui.transparentPanel(new BorderLayout());
        moveTimesPanel.setPreferredSize(new Dimension(360, 260));
        panel.add(moveTimesPanel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the Crosstable card.
     *
     * @return crosstable card
     */
    private JComponent createCrosstableCard() {
        JPanel panel = Ui.transparentPanel(new BorderLayout());
        crosstablePanel.setPreferredSize(new Dimension(360, 220));
        panel.add(crosstablePanel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the Share & export card.
     *
     * @return export card
     */
    private JComponent createExportCard() {
        JPanel panel = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        panel.add(Ui.scroll(exportArea, () -> Theme.PANEL_SOLID), BorderLayout.CENTER);
        panel.add(Ui.buttonRow(FlowLayout.RIGHT,
                Ui.button("Copy PGN", false, event -> copyText.accept(exportArea.getText())),
                Ui.button("Copy Review", false, event -> copySelectedReview())), BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Shows one review-mode card.
     *
     * @param index selected mode index
     */
    private void showReviewMode(int index) {
        String card = switch (index) {
            case 1 -> CARD_TIMES;
            case 2 -> CARD_CROSSTABLE;
            case 3 -> CARD_EXPORT;
            default -> CARD_ANALYSIS;
        };
        reviewCards.show(reviewCardPanel, card);
    }

    /**
     * Creates the review toolbar.
     *
     * @return toolbar component
     */
    private JComponent createToolbar() {
        JPanel toolbar = Ui.transparentPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        JButton review = Ui.button("Review Game", true, event -> runReview());
        toolbar.add(review);
        toolbar.add(Ui.button("Build Study", false, event -> buildStudyArtifactsFromGame()));
        toolbar.add(Ui.button("Retry", false, event -> retrySelected()));
        toolbar.add(Ui.button("Show Move", false, event -> showSelectedMove()));
        toolbar.add(Ui.button("Copy", false, event -> copySelectedReview()));
        toolbar.add(statusLabel);
        return toolbar;
    }

    /**
     * Appends live CLI artifact-production output to the detail pane.
     *
     * @param chunk output chunk
     */
    private void appendArtifactOutput(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        detailArea.append(chunk);
    }

    /**
     * Applies the completed review artifact command.
     *
     * @param result command result
     */
    private void applyArtifactResult(ReviewArtifactProducer.Result result) {
        if (result.exitCode() != 0) {
            statusLabel.setText("Review command failed: exit " + result.exitCode());
            detailArea.setText(result.output());
            return;
        }
        try {
            loadReviewArtifacts(result.reviewJsonl(), result.studyJsonl());
            statusLabel.setText(rowCount() + " CLI review rows loaded");
        } catch (IOException | IllegalArgumentException ex) {
            showArtifactError(ex);
        }
    }

    /**
     * Shows artifact production errors.
     *
     * @param error failure cause
     */
    private void showArtifactError(Exception error) {
        statusLabel.setText("Review command failed");
        detailArea.setText(error == null ? "" : Objects.toString(error.getMessage(), error.getClass().getName()));
        refreshLichessViews();
    }

    /**
     * Refreshes the detail pane from the table selection.
     */
    private void updateDetails() {
        ReviewFinding finding = selectedFinding();
        detailArea.setText(finding == null ? "" : finding.summary());
        notationPanel.setSelectedPly(finding == null ? -1 : finding.ply());
        evaluationTimeline.setSelectedPly(finding == null ? -1 : finding.ply());
        moveTimesPanel.setSelectedPly(finding == null ? -1 : finding.ply());
    }

    /**
     * Refreshes every Lichess-like review pane from the current game and rows.
     */
    private void refreshLichessViews() {
        GameModel model = gameSupplier.get();
        List<ReviewFinding> ordered = orderedFindings();
        evaluationTimeline.setData(ordered);
        moveTimesPanel.setData(ordered);
        notationPanel.setData(model, ordered);
        String pgn = model == null ? "" : model.pgn();
        exportArea.setText(pgn);
        exportArea.setCaretPosition(0);
        crosstablePanel.setData(pgn, ordered);
    }

    /**
     * Returns review findings in mainline ply order.
     *
     * @return ordered findings
     */
    private List<ReviewFinding> orderedFindings() {
        return tableModel.rows().stream()
                .sorted(Comparator.comparingInt(ReviewFinding::ply))
                .toList();
    }

    /**
     * Returns the selected finding.
     *
     * @return selected finding, or null
     */
    private ReviewFinding selectedFinding() {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(selected);
        return tableModel.rowAt(modelRow);
    }

    /**
     * Converts a side-to-move evaluation into White-relative centipawns.
     *
     * @param evaluator position evaluator
     * @param position chess position
     * @return White-relative centipawn score
     */
    private static int whiteCentipawns(Classical evaluator, Position position) {
        int stm = evaluator.evaluate(position);
        return position.isWhiteToMove() ? stm : -stm;
    }

    /**
     * Classifies one move from the mover's static-eval swing.
     *
     * @param loss centipawn loss
     * @param gain centipawn gain
     * @return verdict label
     */
    private static String verdict(int loss, int gain) {
        if (loss >= BLUNDER_CP) {
            return "Blunder";
        }
        if (loss >= MISTAKE_CP) {
            return "Mistake";
        }
        if (loss >= INACCURACY_CP) {
            return "Inaccuracy";
        }
        if (gain >= MISTAKE_CP) {
            return "Strong";
        }
        if (gain >= INACCURACY_CP) {
            return "Good";
        }
        return "Steady";
    }

    /**
     * Returns a sort rank for review verdicts.
     *
     * @param verdict verdict label
     * @return severity rank
     */
    private static int severityRank(String verdict) {
        return switch (verdict) {
            case "Blunder" -> 5;
            case "Mistake" -> 4;
            case "Inaccuracy" -> 3;
            case "Strong" -> 2;
            case "Good" -> 1;
            default -> 0;
        };
    }

    /**
     * Summarizes grounded move-effect tags.
     *
     * @param effects move effects
     * @return compact summary
     */
    private static String summarizeEffects(List<String> effects) {
        if (effects == null || effects.isEmpty()) {
            return "No tactical tag delta";
        }
        StringJoiner joiner = new StringJoiner("; ");
        int count = 0;
        for (String effect : effects) {
            if (effect == null || effect.isBlank()) {
                continue;
            }
            joiner.add(effect.replace("MOVE_EFFECT: ", ""));
            count++;
            if (count >= 3) {
                break;
            }
        }
        return count == 0 ? "No tactical tag delta" : joiner.toString();
    }

    /**
     * Builds one explanation paragraph.
     *
     * @param snapshot ply snapshot
     * @param verdict verdict label
     * @param loss centipawn loss
     * @param beforeWhite before score
     * @param afterWhite after score
     * @param effects move effects
     * @param positionText deterministic position prose
     * @return summary text
     */
    private static String summary(GameModel.PlySnapshot snapshot, String verdict, int loss,
            int beforeWhite, int afterWhite, String effects, String positionText) {
        String side = snapshot.whiteMove() ? "White" : "Black";
        return "Ply " + snapshot.ply() + " " + side + " played " + snapshot.san() + " (" + snapshot.uci()
                + "). Verdict: " + verdict + ". Static swing from White's view: "
                + signedCp(beforeWhite) + " -> " + signedCp(afterWhite)
                + (loss > 0 ? ", " + loss + " cp lost by the mover." : ".")
                + "\nEffects: " + effects
                + "\nPosition: " + positionText;
    }

    /**
     * Formats a centipawn value with sign.
     *
     * @param cp centipawns
     * @return signed centipawn text
     */
    private static String signedCp(int cp) {
        return (cp >= 0 ? "+" : "") + cp;
    }

    /**
     * Formats review completion status.
     *
     * @param findings review findings
     * @return status text
     */
    private static String statusText(List<ReviewFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return "No moves to review";
        }
        long issues = findings.stream()
                .filter(row -> "Blunder".equals(row.verdict())
                        || "Mistake".equals(row.verdict())
                        || "Inaccuracy".equals(row.verdict()))
                .count();
        return findings.size() + " moves reviewed, " + issues + " issues";
    }

    /**
     * Returns whether a verdict marks a mistake-class issue.
     *
     * @param verdict verdict label
     * @return true for inaccuracy, mistake, or blunder
     */
    private static boolean isIssue(String verdict) {
        return "Blunder".equals(verdict) || "Mistake".equals(verdict) || "Inaccuracy".equals(verdict);
    }

    /**
     * Formats centipawns as a pawn-value label.
     *
     * @param cp centipawns
     * @return pawn label
     */
    private static String pawnLabel(int cp) {
        return String.format(java.util.Locale.ROOT, "%+.1f", cp / 100.0d);
    }

    /**
     * Returns the accent color for a review verdict.
     *
     * @param verdict verdict label
     * @return verdict color
     */
    private static Color verdictColor(String verdict) {
        return switch (verdict) {
            case "Blunder" -> Theme.STATUS_ERROR_TEXT;
            case "Mistake" -> Theme.STATUS_WARNING_TEXT;
            case "Inaccuracy" -> Theme.STATUS_INFO_TEXT;
            case "Strong", "Good" -> Theme.STATUS_SUCCESS_TEXT;
            default -> Theme.MUTED;
        };
    }

    /**
     * Paints an empty chart state.
     *
     * @param g graphics
     * @param bounds chart bounds
     * @param title empty-state title
     */
    private static void paintEmptyChart(Graphics2D g, Rectangle bounds, String title) {
        g.setColor(Theme.PANEL_SOLID);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        Ui.paintEmptyState(g, bounds, title, "Run Review Game to populate this pane.");
    }

    /**
     * Draws text with an explicit font without mutating the graphics context font.
     *
     * @param g graphics
     * @param text text
     * @param font font
     * @param color color
     * @param x x-coordinate
     * @param baseline baseline y-coordinate
     */
    private static void drawText(Graphics2D g, String text, Font font, Color color, float x, float baseline) {
        if (text == null || text.isEmpty()) {
            return;
        }
        g.setColor(color);
        new TextLayout(text, font, g.getFontRenderContext()).draw(g, x, baseline);
    }

    /**
     * Lichess-like evaluation timeline for reviewed plies.
     */
    private static final class EvaluationTimelinePanel extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Review rows in ply order.
         */
        private List<ReviewFinding> rows = List.of();

        /**
         * Selected ply, or -1.
         */
        private int selectedPly = -1;

        /**
         * Creates the timeline.
         */
        EvaluationTimelinePanel() {
            setOpaque(true);
            setBackground(Theme.PANEL_SOLID);
            setToolTipText("Computer analysis timeline");
        }

        /**
         * Replaces timeline data.
         *
         * @param nextRows review rows
         */
        void setData(List<ReviewFinding> nextRows) {
            rows = nextRows == null ? List.of() : List.copyOf(nextRows);
            repaint();
        }

        /**
         * Sets the selected ply marker.
         *
         * @param ply selected ply
         */
        void setSelectedPly(int ply) {
            selectedPly = ply;
            repaint();
        }

        /**
         * Paints the timeline.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintTimeline(g);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints the timeline body.
         *
         * @param g graphics
         */
        private void paintTimeline(Graphics2D g) {
            int width = getWidth();
            int height = getHeight();
            if (rows.isEmpty() || width < 20 || height < 20) {
                paintEmptyChart(g, new Rectangle(0, 0, width, height), "No computer analysis");
                return;
            }
            int plotX = 8;
            int plotY = 28;
            int plotW = Math.max(1, width - 16);
            int plotH = Math.max(1, height - 42);
            paintPlotBackground(g, plotX, plotY, plotW, plotH);
            int range = evalRange(rows);
            int zeroY = evalY(plotY, plotH, 0, range);
            g.setColor(Theme.withAlpha(Theme.TEXT, 80));
            g.drawLine(plotX, zeroY, plotX + plotW, zeroY);
            paintPhaseMarker(g, "Opening", 10, plotX, plotY, plotW, plotH);
            paintPhaseMarker(g, "Middlegame", 30, plotX, plotY, plotW, plotH);
            paintEvalShape(g, plotX, plotY, plotW, plotH, zeroY, range);
            paintSelectedPly(g, plotX, plotY, plotW, plotH);
            Font headerFont = Theme.font(12, Font.PLAIN);
            FontMetrics headerMetrics = g.getFontMetrics(headerFont);
            drawText(g, "Computer analysis", headerFont, Theme.MUTED, plotX, 18);
            ReviewFinding latest = rows.get(rows.size() - 1);
            String label = pawnLabel(latest.afterWhiteCp()) + " after " + latest.san();
            drawText(g, label, headerFont, Theme.TEXT,
                    Math.max(plotX, width - headerMetrics.stringWidth(label) - 10), 18);
        }

        /**
         * Paints the chart background.
         *
         * @param g graphics
         * @param x x-coordinate
         * @param y y-coordinate
         * @param w width
         * @param h height
         */
        private static void paintPlotBackground(Graphics2D g, int x, int y, int w, int h) {
            g.setColor(Theme.PANEL_SOLID);
            g.fillRect(x, y, w, h);
            g.setColor(Theme.withAlpha(Theme.LINE, 150));
            g.drawLine(x, y, x + w, y);
            g.drawLine(x, y + h, x + w, y + h);
        }

        /**
         * Paints opening/middlegame phase markers.
         *
         * @param g graphics
         * @param label marker label
         * @param ply phase ply
         * @param x x-coordinate
         * @param y y-coordinate
         * @param w width
         * @param h height
         */
        private void paintPhaseMarker(Graphics2D g, String label, int ply, int x, int y, int w, int h) {
            int maxPly = Math.max(1, rows.get(rows.size() - 1).ply());
            if (ply >= maxPly) {
                return;
            }
            int markerX = x + (int) Math.round((ply / (double) maxPly) * w);
            g.setColor(Theme.withAlpha(Theme.MUTED, 130));
            g.drawLine(markerX, y, markerX, y + h);
            Graphics2D rotated = (Graphics2D) g.create();
            try {
                rotated.rotate(Math.PI / 2.0d, markerX + 4, y + 2);
                drawText(rotated, label, Theme.font(11, Font.PLAIN), Theme.MUTED, markerX + 4, y + 2);
            } finally {
                rotated.dispose();
            }
        }

        /**
         * Paints the evaluation area and line.
         *
         * @param g graphics
         * @param x x-coordinate
         * @param y y-coordinate
         * @param w width
         * @param h height
         * @param zeroY zero line y-coordinate
         * @param range centipawn range
         */
        private void paintEvalShape(Graphics2D g, int x, int y, int w, int h, int zeroY, int range) {
            Path2D.Double line = new Path2D.Double();
            Path2D.Double fill = new Path2D.Double();
            for (int i = 0; i < rows.size(); i++) {
                ReviewFinding row = rows.get(i);
                double px = sampleX(x, w, i);
                double py = evalY(y, h, row.afterWhiteCp(), range);
                if (i == 0) {
                    line.moveTo(px, py);
                    fill.moveTo(px, zeroY);
                    fill.lineTo(px, py);
                } else {
                    line.lineTo(px, py);
                    fill.lineTo(px, py);
                }
            }
            fill.lineTo(sampleX(x, w, rows.size() - 1), zeroY);
            fill.closePath();
            g.setColor(Theme.withAlpha(Theme.MUTED, 120));
            g.fill(fill);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.ACCENT);
            g.draw(line);
        }

        /**
         * Paints the selected ply marker.
         *
         * @param g graphics
         * @param x x-coordinate
         * @param y y-coordinate
         * @param w width
         * @param h height
         */
        private void paintSelectedPly(Graphics2D g, int x, int y, int w, int h) {
            if (selectedPly < 1 || rows.isEmpty()) {
                return;
            }
            int maxPly = Math.max(1, rows.get(rows.size() - 1).ply());
            int markerX = x + (int) Math.round((selectedPly / (double) maxPly) * w);
            g.setColor(Theme.ACCENT);
            g.drawLine(markerX, y, markerX, y + h);
        }

        /**
         * Returns sample x-coordinate.
         *
         * @param x plot x
         * @param w plot width
         * @param index row index
         * @return x-coordinate
         */
        private double sampleX(int x, int w, int index) {
            if (rows.size() <= 1) {
                return x + w;
            }
            return x + (index / (double) (rows.size() - 1)) * w;
        }
    }

    /**
     * Lichess-like move-times pane backed by deterministic review effort.
     */
    private static final class MoveTimesPanel extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Review rows in ply order.
         */
        private List<ReviewFinding> rows = List.of();

        /**
         * Selected ply, or -1.
         */
        private int selectedPly = -1;

        /**
         * Creates the move-times chart.
         */
        MoveTimesPanel() {
            setOpaque(true);
            setBackground(Theme.PANEL_SOLID);
            setToolTipText("Move effort chart derived from review swings");
        }

        /**
         * Replaces chart data.
         *
         * @param nextRows review rows
         */
        void setData(List<ReviewFinding> nextRows) {
            rows = nextRows == null ? List.of() : List.copyOf(nextRows);
            repaint();
        }

        /**
         * Sets the selected ply marker.
         *
         * @param ply selected ply
         */
        void setSelectedPly(int ply) {
            selectedPly = ply;
            repaint();
        }

        /**
         * Paints the chart.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintChart(g);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints the move-times body.
         *
         * @param g graphics
         */
        private void paintChart(Graphics2D g) {
            int width = getWidth();
            int height = getHeight();
            if (rows.isEmpty() || width < 20 || height < 20) {
                paintEmptyChart(g, new Rectangle(0, 0, width, height), "No move times");
                return;
            }
            int x = 8;
            int y = 34;
            int w = Math.max(1, width - 16);
            int h = Math.max(1, height - 58);
            int zeroY = y + h / 2;
            g.setColor(Theme.PANEL_SOLID);
            g.fillRect(x, y, w, h);
            g.setColor(Theme.withAlpha(Theme.LINE, 160));
            g.drawLine(x, zeroY, x + w, zeroY);
            double slot = w / (double) rows.size();
            int maxEffort = rows.stream().mapToInt(MoveTimesPanel::effort).max().orElse(1);
            for (int i = 0; i < rows.size(); i++) {
                ReviewFinding row = rows.get(i);
                boolean whiteMove = row.ply() % 2 == 1;
                int bar = (int) Math.round((effort(row) / (double) maxEffort) * (h * 0.42));
                int bx = x + (int) Math.round(i * slot);
                int bw = Math.max(2, (int) Math.round(slot) - 2);
                if (row.ply() == selectedPly) {
                    g.setColor(Theme.ACCENT);
                    g.drawLine(bx + bw / 2, y, bx + bw / 2, y + h);
                }
                g.setColor(whiteMove ? Theme.withAlpha(Theme.TEXT, 210) : Theme.withAlpha(Theme.BG, 220));
                int by = whiteMove ? zeroY - bar : zeroY;
                g.fillRect(bx, by, bw, bar);
                g.setColor(Theme.withAlpha(Theme.LINE, 140));
                g.drawRect(bx, by, bw, bar);
            }
            paintClockLines(g, x, y, w, h);
            Font headerFont = Theme.font(12, Font.PLAIN);
            FontMetrics headerMetrics = g.getFontMetrics(headerFont);
            drawText(g, "Move times", headerFont, Theme.MUTED, x, 20);
            String duration = "Duration --:--";
            drawText(g, duration, headerFont, Theme.MUTED,
                    width - headerMetrics.stringWidth(duration) - 12, height - 12);
        }

        /**
         * Paints mirrored clock-trend lines.
         *
         * @param g graphics
         * @param x x-coordinate
         * @param y y-coordinate
         * @param w width
         * @param h height
         */
        private void paintClockLines(Graphics2D g, int x, int y, int w, int h) {
            if (rows.size() < 2) {
                return;
            }
            int total = rows.stream().mapToInt(MoveTimesPanel::effort).sum();
            int spent = 0;
            Path2D.Double upper = new Path2D.Double();
            Path2D.Double lower = new Path2D.Double();
            for (int i = 0; i < rows.size(); i++) {
                spent += effort(rows.get(i));
                double px = x + (i / (double) (rows.size() - 1)) * w;
                double share = total <= 0 ? 0.0d : spent / (double) total;
                double upperY = y + 8 + share * h * 0.28;
                double lowerY = y + h - 8 - share * h * 0.28;
                if (i == 0) {
                    upper.moveTo(px, upperY);
                    lower.moveTo(px, lowerY);
                } else {
                    upper.lineTo(px, upperY);
                    lower.lineTo(px, lowerY);
                }
            }
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.STATUS_INFO_TEXT);
            g.draw(upper);
            g.draw(lower);
        }

        /**
         * Returns deterministic effort for one move.
         *
         * @param row review finding
         * @return effort units
         */
        private static int effort(ReviewFinding row) {
            int swing = Math.abs(row.afterWhiteCp() - row.beforeWhiteCp());
            return Math.max(4, Math.min(160, 8 + row.lossCp() / 4 + swing / 18));
        }
    }

    /**
     * Lichess-like crosstable summary.
     */
    private static final class CrosstablePanel extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * White player label.
         */
        private String white = "White";

        /**
         * Black player label.
         */
        private String black = "Black";

        /**
         * Result label.
         */
        private String result = "*";

        /**
         * Issue count.
         */
        private int issueCount;

        /**
         * Creates the crosstable view.
         */
        CrosstablePanel() {
            setOpaque(true);
            setBackground(Theme.PANEL_SOLID);
            setToolTipText("Crosstable summary");
        }

        /**
         * Replaces crosstable data.
         *
         * @param pgn game PGN
         * @param rows review rows
         */
        void setData(String pgn, List<ReviewFinding> rows) {
            Game game = Pgn.parseGame(pgn);
            if (game != null) {
                white = tag(game, "White", "White");
                black = tag(game, "Black", "Black");
                result = tag(game, "Result", game.getResult());
            }
            issueCount = rows == null ? 0 : (int) rows.stream().filter(row -> isIssue(row.verdict())).count();
            repaint();
        }

        /**
         * Paints the crosstable row.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintCrosstable(g);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints the crosstable body.
         *
         * @param g graphics
         */
        private void paintCrosstable(Graphics2D g) {
            int width = getWidth();
            int height = getHeight();
            if (width < 20 || height < 20) {
                return;
            }
            g.setColor(Theme.PANEL_SOLID);
            g.fillRect(0, 0, width, height);
            int rowH = 64;
            int y = Math.max(44, height / 2 - rowH / 2);
            g.setColor(Theme.withAlpha(Theme.ELEVATED_SOLID, 235));
            g.fillRoundRect(8, y, Math.max(1, width - 16), rowH, 8, 8);
            int scoreX = Math.max(90, width / 2 - 72);
            int nameX = scoreX + 72;
            Font captionFont = Theme.font(12, Font.PLAIN);
            Font scoreFont = Theme.mono(13);
            Font whiteFont = Theme.font(13, Font.BOLD);
            Font blackFont = Theme.font(13, Font.PLAIN);
            drawText(g, "Crosstable", captionFont, Theme.MUTED, 12, 24);
            drawText(g, scoreForWhite(), scoreFont, Theme.STATUS_SUCCESS_TEXT, scoreX, y + 22);
            drawText(g, scoreForBlack(), scoreFont, Theme.STATUS_ERROR_TEXT, scoreX, y + 50);
            int nameWidth = Math.max(40, width - nameX - 34);
            drawText(g, Ui.elide(white, g.getFontMetrics(whiteFont), nameWidth),
                    whiteFont, Theme.TEXT, nameX, y + 22);
            drawText(g, Ui.elide(black, g.getFontMetrics(blackFont), nameWidth),
                    blackFont, Theme.TEXT, nameX, y + 50);
            String issue = issueCount + " issue" + (issueCount == 1 ? "" : "s");
            Font issueFont = Theme.font(13, Font.PLAIN);
            FontMetrics issueMetrics = g.getFontMetrics(issueFont);
            drawText(g, issue, issueFont,
                    issueCount == 0 ? Theme.STATUS_SUCCESS_TEXT : Theme.STATUS_WARNING_TEXT,
                    Math.max(nameX, width - issueMetrics.stringWidth(issue) - 20), y + 36);
        }

        /**
         * Returns one PGN tag.
         *
         * @param game game
         * @param key tag key
         * @param fallback fallback value
         * @return tag value
         */
        private static String tag(Game game, String key, String fallback) {
            String value = game.getTags().get(key);
            return value == null || value.isBlank() ? fallback : value;
        }

        /**
         * Returns White score text.
         *
         * @return score
         */
        private String scoreForWhite() {
            return switch (result) {
                case "1-0" -> "1";
                case "0-1" -> "0";
                case "1/2-1/2" -> "1/2";
                default -> "-";
            };
        }

        /**
         * Returns Black score text.
         *
         * @return score
         */
        private String scoreForBlack() {
            return switch (result) {
                case "1-0" -> "0";
                case "0-1" -> "1";
                case "1/2-1/2" -> "1/2";
                default -> "-";
            };
        }
    }

    /**
     * Lichess-like move notation and verdict lane.
     */
    private static final class ReviewNotationPanel extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Mainline snapshots.
         */
        private List<GameModel.PlySnapshot> moves = List.of();

        /**
         * Review findings in ply order.
         */
        private List<ReviewFinding> rows = List.of();

        /**
         * Plain text notation cache.
         */
        private String notationText = "";

        /**
         * Selected ply.
         */
        private int selectedPly = -1;

        /**
         * Creates the notation panel.
         */
        ReviewNotationPanel() {
            setOpaque(true);
            setBackground(Theme.PANEL_SOLID);
            setToolTipText("Computer analysis notation");
            setPreferredSize(new Dimension(360, 360));
        }

        /**
         * Replaces notation data.
         *
         * @param model game model
         * @param nextRows review rows
         */
        void setData(GameModel model, List<ReviewFinding> nextRows) {
            moves = model == null ? List.of() : model.mainlineSnapshots();
            rows = nextRows == null ? List.of() : List.copyOf(nextRows);
            notationText = buildNotationText();
            int preferredH = Math.max(280, 96 + moves.size() * 24 + issueCount() * 28);
            setPreferredSize(new Dimension(360, preferredH));
            revalidate();
            repaint();
        }

        /**
         * Sets the selected ply.
         *
         * @param ply selected ply
         */
        void setSelectedPly(int ply) {
            selectedPly = ply;
            repaint();
        }

        /**
         * Returns visible notation text.
         *
         * @return notation text
         */
        String notationText() {
            return notationText;
        }

        /**
         * Paints the notation lane.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintNotation(g);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints notation body.
         *
         * @param g graphics
         */
        private void paintNotation(Graphics2D g) {
            int width = getWidth();
            int y = paintEngineHeader(g, width);
            if (moves.isEmpty()) {
                paintEmptyChart(g, new Rectangle(0, y, width, Math.max(120, getHeight() - y)), "No notation");
                return;
            }
            FontMetrics metrics = g.getFontMetrics();
            int icon = NotationPainter.iconSize(metrics);
            int x = 8;
            int maxX = Math.max(80, width - 8);
            ReviewFinding pendingIssue = null;
            for (GameModel.PlySnapshot move : moves) {
                ReviewFinding finding = findingForPly(move.ply());
                String label = moveLabel(move);
                int tokenW = Math.max(34, NotationPainter.width(label, metrics, icon) + 14);
                if (x + tokenW > maxX) {
                    x = 8;
                    y += 28;
                }
                paintMoveToken(g, label, x, y, tokenW, move.ply(), finding);
                x += tokenW + 5;
                if (finding != null && isIssue(finding.verdict())) {
                    pendingIssue = finding;
                    if (x > maxX - 140) {
                        x = 24;
                        y += 28;
                    }
                    int issueW = Math.min(maxX - x, Math.max(120,
                            g.getFontMetrics().stringWidth(issueText(finding)) + 22));
                    paintIssueText(g, finding, x, y, issueW);
                    x += issueW + 6;
                }
            }
            if (pendingIssue != null) {
                y += 30;
                drawText(g, "Last issue: " + issueText(pendingIssue),
                        Theme.font(12, Font.PLAIN), Theme.MUTED, 8, y);
            }
        }

        /**
         * Paints the compact engine/review header.
         *
         * @param g graphics
         * @param width panel width
         * @return next y coordinate
         */
        private int paintEngineHeader(Graphics2D g, int width) {
            int y = 8;
            g.setColor(Theme.withAlpha(Theme.ELEVATED_SOLID, 230));
            g.fillRoundRect(8, y, Math.max(1, width - 16), 56, 8, 8);
            g.setColor(Theme.STATUS_SUCCESS_TEXT);
            g.fillRoundRect(16, y + 10, 44, 22, 22, 22);
            drawText(g, "\u2713", Theme.font(13, Font.BOLD), Theme.PANEL_SOLID, 34, y + 26);
            drawText(g, headerEval(), Theme.font(18, Font.BOLD), Theme.TEXT, 72, y + 24);
            Font metaFont = Theme.font(11, Font.BOLD);
            drawText(g, "CRTK classical", metaFont, Theme.MUTED, 142, y + 17);
            drawText(g, "LOCAL", metaFont, Theme.STATUS_SUCCESS_TEXT, 142, y + 34);
            Font pvFont = Theme.mono(11);
            FontMetrics pvMetrics = g.getFontMetrics(pvFont);
            String pv = principalLine();
            drawText(g, Ui.elide(pv, pvMetrics, Math.max(10, width - 24)), pvFont, Theme.TEXT, 10, y + 78);
            return y + 100;
        }

        /**
         * Paints one SAN move chip.
         *
         * @param g graphics
         * @param label label text
         * @param x x-coordinate
         * @param y baseline y
         * @param width chip width
         * @param ply ply
         * @param finding review finding
         */
        private void paintMoveToken(Graphics2D g, String label, int x, int y, int width,
                int ply, ReviewFinding finding) {
            boolean selected = ply == selectedPly;
            Color fill = selected ? Theme.withAlpha(Theme.ACCENT, 190)
                    : finding != null && isIssue(finding.verdict())
                            ? Theme.withAlpha(verdictColor(finding.verdict()), 52)
                            : Theme.withAlpha(Theme.ELEVATED_SOLID, 210);
            g.setColor(fill);
            g.fillRoundRect(x, y - 17, width, 22, 6, 6);
            NotationPainter.draw(g, label, x + 6, y, width - 10,
                    selected ? Theme.PANEL_SOLID : Theme.TEXT);
        }

        /**
         * Paints issue text after a move.
         *
         * @param g graphics
         * @param finding finding
         * @param x x-coordinate
         * @param y baseline y
         * @param width maximum width
         */
        private void paintIssueText(Graphics2D g, ReviewFinding finding, int x, int y, int width) {
            g.setColor(verdictColor(finding.verdict()));
            g.fillRoundRect(x, y - 14, 14, 14, 4, 4);
            String text = issueText(finding);
            Font issueFont = Theme.font(12, Font.PLAIN);
            drawText(g, Ui.elide(text, g.getFontMetrics(issueFont), Math.max(20, width - 20)),
                    issueFont, verdictColor(finding.verdict()), x + 20, y);
        }

        /**
         * Returns the issue text for one finding.
         *
         * @param finding finding
         * @return issue text
         */
        private static String issueText(ReviewFinding finding) {
            String best = bestSummary(finding);
            if (!best.isBlank()) {
                return finding.verdict() + ". " + best + " was best.";
            }
            return finding.verdict() + ". Static swing " + finding.lossCp() + " cp.";
        }

        /**
         * Extracts best-move text from artifact summaries when present.
         *
         * @param finding finding
         * @return best move text, or blank
         */
        private static String bestSummary(ReviewFinding finding) {
            String summary = finding.summary();
            int start = summary == null ? -1 : summary.indexOf("Best: ");
            if (start < 0) {
                return "";
            }
            int from = start + "Best: ".length();
            int end = summary.indexOf('\n', from);
            String text = (end < 0 ? summary.substring(from) : summary.substring(from, end)).trim();
            int paren = text.indexOf(" (");
            return paren < 0 ? text : text.substring(0, paren).trim();
        }

        /**
         * Returns header evaluation text.
         *
         * @return evaluation label
         */
        private String headerEval() {
            if (rows.isEmpty()) {
                return "+0.0";
            }
            return pawnLabel(rows.get(rows.size() - 1).afterWhiteCp());
        }

        /**
         * Returns a compact principal line from the current game.
         *
         * @return principal line
         */
        private String principalLine() {
            StringBuilder sb = new StringBuilder();
            for (GameModel.PlySnapshot move : moves) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(moveLabel(move));
                if (sb.length() > 180) {
                    break;
                }
            }
            return sb.toString();
        }

        /**
         * Finds a review row for a ply.
         *
         * @param ply ply
         * @return finding, or null
         */
        private ReviewFinding findingForPly(int ply) {
            for (ReviewFinding row : rows) {
                if (row.ply() == ply) {
                    return row;
                }
            }
            return null;
        }

        /**
         * Builds the notation text cache.
         *
         * @return notation text
         */
        private String buildNotationText() {
            StringBuilder sb = new StringBuilder();
            for (GameModel.PlySnapshot move : moves) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(moveLabel(move));
                ReviewFinding finding = findingForPly(move.ply());
                if (finding != null && isIssue(finding.verdict())) {
                    sb.append(" {").append(issueText(finding)).append('}');
                }
            }
            return sb.toString();
        }

        /**
         * Counts issue rows.
         *
         * @return issue count
         */
        private int issueCount() {
            int count = 0;
            for (ReviewFinding row : rows) {
                if (isIssue(row.verdict())) {
                    count++;
                }
            }
            return count;
        }

        /**
         * Formats a move label with move numbers.
         *
         * @param move move snapshot
         * @return move label
         */
        private static String moveLabel(GameModel.PlySnapshot move) {
            if (move.whiteMove()) {
                return ((move.ply() + 1) / 2) + ". " + move.san();
            }
            return move.san();
        }
    }

    /**
     * Returns a dynamic evaluation range for charts.
     *
     * @param rows review rows
     * @return centipawn range
     */
    private static int evalRange(List<ReviewFinding> rows) {
        int max = 300;
        for (ReviewFinding row : rows) {
            max = Math.max(max, Math.abs(row.beforeWhiteCp()));
            max = Math.max(max, Math.abs(row.afterWhiteCp()));
        }
        return Math.min(1800, max + 80);
    }

    /**
     * Converts a centipawn score to chart y-coordinate.
     *
     * @param y chart y
     * @param h chart height
     * @param cp centipawns
     * @param range centipawn range
     * @return y-coordinate
     */
    private static int evalY(int y, int h, int cp, int range) {
        int clamped = Math.max(-range, Math.min(range, cp));
        double normalized = (range - clamped) / (double) (range * 2);
        return y + (int) Math.round(normalized * h);
    }

    /**
     * One post-game review finding.
     *
     * @param ply one-based ply
     * @param san SAN move
     * @param uci UCI move
     * @param beforeFen position before the move
     * @param afterFen position after the move
     * @param verdict move verdict
     * @param lossCp centipawns lost by mover
     * @param beforeWhiteCp White-relative score before the move
     * @param afterWhiteCp White-relative score after the move
     * @param effects grounded move-effect tags
     * @param summary explanation text
     */
    public record ReviewFinding(
            int ply,
            String san,
            String uci,
            String beforeFen,
            String afterFen,
            String verdict,
            int lossCp,
            int beforeWhiteCp,
            int afterWhiteCp,
            String effects,
            String summary) {
    }

    /**
     * Review findings table model.
     */
    private static final class ReviewTableModel extends AbstractTableModel {

        /**
         * Serialization identifier for Swing table model compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Column labels.
         */
        private static final String[] COLUMNS = { "Ply", "Move", "Verdict", "Loss", "Eval", "Effects" };

        /**
         * Visible review rows.
         */
        private List<ReviewFinding> rows = List.of();

        /**
         * Replaces review rows.
         *
         * @param nextRows source next rows
         */
        private void setRows(List<ReviewFinding> nextRows) {
            rows = List.copyOf(nextRows);
            fireTableDataChanged();
        }

        /**
         * Returns all review rows.
         *
         * @return rows
         */
        private List<ReviewFinding> rows() {
            return rows;
        }

        /**
         * Returns one row by model index.
         *
         * @param row row index
         * @return finding, or null
         */
        private ReviewFinding rowAt(int row) {
            return row < 0 || row >= rows.size() ? null : rows.get(row);
        }

        /**
         * Returns the row count.
         *
         * @return row count
         */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /**
         * Returns the column count.
         *
         * @return column count
         */
        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        /**
         * Returns a column name.
         *
         * @param column column index
         * @return column name
         */
        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        /**
         * Returns a column type.
         *
         * @param columnIndex zero-based column index
         * @return column class
         */
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 || columnIndex == 3 ? Integer.class : String.class;
        }

        /**
         * Returns a table value.
         *
         * @param rowIndex zero-based row index
         * @param columnIndex zero-based column index
         * @return cell value
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ReviewFinding row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> Integer.valueOf(row.ply());
                case 1 -> row.san();
                case 2 -> row.verdict();
                case 3 -> Integer.valueOf(row.lossCp());
                case 4 -> signedCp(row.beforeWhiteCp()) + " -> " + signedCp(row.afterWhiteCp());
                case 5 -> row.effects();
                default -> "";
            };
        }
    }
}
