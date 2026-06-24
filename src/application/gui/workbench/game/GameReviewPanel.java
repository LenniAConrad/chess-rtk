package application.gui.workbench.game;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import chess.core.Position;
import chess.describe.ClassicalPositionDescriptionGenerator;
import chess.describe.PositionDescriptionDetail;
import chess.describe.PositionDescriptionInput;
import chess.eval.Classical;
import chess.tag.MoveEffect;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private final JTextArea detailArea = new JTextArea();

    /**
     * Compact review status.
     */
    private final JLabel statusLabel = Ui.label("Review not run");

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

        add(createToolbar(), BorderLayout.NORTH);
        add(Ui.titled("Key Moves", Ui.scroll(table)), BorderLayout.CENTER);
        add(Ui.titled("Explanation", Ui.scroll(detailArea)), BorderLayout.SOUTH);
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
    }

    /**
     * Refreshes the detail pane from the table selection.
     */
    private void updateDetails() {
        ReviewFinding finding = selectedFinding();
        detailArea.setText(finding == null ? "" : finding.summary());
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
