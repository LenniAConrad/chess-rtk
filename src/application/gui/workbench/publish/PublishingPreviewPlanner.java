package application.gui.workbench.publish;

import application.gui.workbench.game.FenInput;
import application.gui.workbench.publish.PublishSampleData.SampleItem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds Publish preview data and preflight summaries from the current controls.
 */
final class PublishingPreviewPlanner {

    /**
     * Read-only view of the Publish controls needed for preview planning.
     */
    abstract static class Context {

        /**
         * Selected publishing task.
         *
         * @return task
         */
        abstract PublishTask task();

        /**
         * Selected diagram source.
         *
         * @return source
         */
        abstract PublishSource source();

        /**
         * Current title field text.
         *
         * @return title
         */
        abstract String title();

        /**
         * Current subtitle field text.
         *
         * @return subtitle
         */
        abstract String subtitle();

        /**
         * Main input path text.
         *
         * @return input path
         */
        abstract String inputPath();

        /**
         * Primary output path text.
         *
         * @return output path
         */
        abstract String outputPath();

        /**
         * Optional manifest-output path text.
         *
         * @return manifest output
         */
        abstract String manifestOutputPath();

        /**
         * Optional interior-PDF path text.
         *
         * @return interior PDF
         */
        abstract String pdfOutputPath();

        /**
         * Optional cover-output path text.
         *
         * @return cover output
         */
        abstract String coverOutputPath();

        /**
         * Optional item-limit text.
         *
         * @return limit text
         */
        abstract String limit();

        /**
         * Optional page-count text.
         *
         * @return pages text
         */
        abstract String pages();

        /**
         * Whether diagram/study boards render black down.
         *
         * @return true when flipped
         */
        abstract boolean flip();

        /**
         * Whether FEN captions are hidden.
         *
         * @return true when hidden
         */
        abstract boolean noFen();

        /**
         * Current board FEN.
         *
         * @return FEN
         */
        abstract String currentFen();

        /**
         * Current game FEN list.
         *
         * @return FEN list
         */
        abstract String gameFenList();

        /**
         * Current game length.
         *
         * @return last ply
         */
        abstract int gameLastPly();

        /**
         * Current batch input text.
         *
         * @return batch input
         */
        abstract String batchInputText();
    }

    /**
     * Prevents instantiation.
     */
    private PublishingPreviewPlanner() {
        // utility
    }

    /**
     * Builds the publishing preview for the current form context.
     *
     * @param context publishing form context
     * @param issue preflight issue text, or {@code null}
     * @return preview model for the selected publishing mode
     */
    static PublishPreview.Preview preview(Context context, String issue) {
        PublishTask task = context.task();
        String title = context.title();
        return new PublishPreview.Preview(
                task.toString(),
                title.isEmpty() && usesSampleTitle(task) ? PublishSampleData.SAMPLE_TITLE : title,
                context.subtitle(),
                sourcePreview(context),
                outputPreview(context),
                issue == null,
                issue == null ? "" : issue,
                estimatedPages(context),
                task == PublishTask.COVER,
                task == PublishTask.DIAGRAMS,
                context.flip(),
                context.noFen(),
                previewItems(context));
    }

    /**
     * Returns the context line.
     *
     * @param context source context
     * @param issue issue description
     * @return context line text
     */
    static String contextLine(Context context, String issue) {
        return context.task() + " · " + context.source() + " · "
                + (issue == null ? "Ready to publish" : "Needs attention");
    }

    /**
     * Returns the preflight issue.
     *
     * @param context source context
     * @return preflight issue text
     */
    static String preflightIssue(Context context) {
        if (context.task() != PublishTask.DIAGRAMS) {
            return null;
        }
        return switch (context.source()) {
            case GAME_PGN -> context.gameLastPly() <= 0
                    ? "Play or import at least one game move before exporting PGN diagrams." : null;
            case BATCH_FENS -> batchFenIssue(context);
            case CURRENT_FEN, EXISTING_FILE -> null;
        };
    }

    /**
     * Returns the pdf path.
     *
     * @param context source context
     * @return pdf path
     */
    static Path currentPdfPath(Context context) {
        String value = switch (context.task()) {
            case DIAGRAMS, RENDER, STUDY -> context.outputPath();
            case COLLECTION -> context.pdfOutputPath();
            case COVER -> context.outputPath();
        };
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    /**
     * Returns the uses sample title.
     *
     * @param task background task
     * @return true when uses sample title
     */
    private static boolean usesSampleTitle(PublishTask task) {
        return task == PublishTask.COLLECTION || task == PublishTask.STUDY
                || task == PublishTask.RENDER || task == PublishTask.COVER;
    }

    /**
     * Returns the preview items.
     *
     * @param context source context
     * @return preview items
     */
    private static List<SampleItem> previewItems(Context context) {
        if (context.task() == PublishTask.DIAGRAMS) {
            return diagramPreviewItems(context);
        }
        if (context.task() == PublishTask.COLLECTION) {
            return PublishSampleData.puzzleItems();
        }
        return PublishSampleData.studyItems();
    }

    /**
     * Returns the diagram preview items.
     *
     * @param context source context
     * @return diagram preview items
     */
    private static List<SampleItem> diagramPreviewItems(Context context) {
        List<SampleItem> items = switch (context.source()) {
            case CURRENT_FEN -> {
                String fen = context.currentFen();
                yield fen == null || fen.isBlank() ? List.of()
                        : List.of(new SampleItem(fen, "Current position", FenInput.compactPreview(fen)));
            }
            case GAME_PGN -> fenListItems(context.gameFenList(), "Move ");
            case BATCH_FENS -> fenListItems(context.batchInputText(), "Position ");
            case EXISTING_FILE -> List.of();
        };
        return items.isEmpty() ? PublishSampleData.studyItems() : items;
    }

    /**
     * Returns the FEN list items.
     *
     * @param text text to render or parse
     * @param captionPrefix source caption prefix
     * @return FEN list items
     */
    private static List<SampleItem> fenListItems(String text, String captionPrefix) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<SampleItem> items = new ArrayList<>();
        for (String line : text.strip().split("\\R")) {
            String fen = line.trim();
            if (fen.isEmpty() || fen.startsWith("#")) {
                continue;
            }
            items.add(new SampleItem(fen, captionPrefix + (items.size() + 1), FenInput.compactPreview(fen)));
            if (items.size() >= 12) {
                break;
            }
        }
        return items;
    }

    /**
     * Returns the source preview.
     *
     * @param context source context
     * @return source preview text
     */
    private static String sourcePreview(Context context) {
        if (context.task() != PublishTask.DIAGRAMS) {
            return pathOrMissing("input file", context.inputPath());
        }
        return switch (context.source()) {
            case CURRENT_FEN -> "current board FEN (" + FenInput.compactPreview(context.currentFen()) + ")";
            case GAME_PGN -> context.gameLastPly() <= 0
                    ? "workbench game PGN (no moves)"
                    : "workbench game PGN (" + context.gameLastPly() + " ply)";
            case BATCH_FENS -> batchFenPreview(context);
            case EXISTING_FILE -> pathOrMissing("diagram input file", context.inputPath());
        };
    }

    /**
     * Returns the output preview.
     *
     * @param context source context
     * @return output preview text
     */
    private static String outputPreview(Context context) {
        return switch (context.task()) {
            case DIAGRAMS, RENDER, STUDY -> pathOrMissing("PDF", context.outputPath())
                    + optionalOutput("manifest copy", context.manifestOutputPath(), context.task() == PublishTask.STUDY)
                    + optionalOutput("cover", context.coverOutputPath(), context.task() == PublishTask.STUDY);
            case COLLECTION -> pathOrMissing("manifest", context.outputPath())
                    + optionalOutput("interior PDF", context.pdfOutputPath(), true)
                    + optionalOutput("cover", context.coverOutputPath(), true);
            case COVER -> pathOrMissing("cover PDF", context.outputPath())
                    + optionalOutput("interior PDF", context.pdfOutputPath(), true);
        };
    }

    /**
     * Returns the estimated pages.
     *
     * @param context source context
     * @return estimated pages
     */
    private static int estimatedPages(Context context) {
        PublishTask task = context.task();
        Integer explicitPages = optionalPositiveInteger(context.pages());
        if (explicitPages != null && (task == PublishTask.COLLECTION || task == PublishTask.STUDY
                || task == PublishTask.COVER)) {
            return explicitPages.intValue();
        }
        return switch (task) {
            case COVER -> 1;
            case DIAGRAMS -> estimatedDiagramPages(context);
            case RENDER -> Math.max(1, optionalPositiveInteger(context.limit(), 12) / 2);
            case COLLECTION -> Math.max(8, optionalPositiveInteger(context.limit(), 64));
            case STUDY -> Math.max(12, context.gameLastPly() + 8);
        };
    }

    /**
     * Returns the estimated diagram pages.
     *
     * @param context source context
     * @return estimated diagram pages
     */
    private static int estimatedDiagramPages(Context context) {
        return switch (context.source()) {
            case CURRENT_FEN -> 1;
            case GAME_PGN -> Math.max(1, Math.max(1, context.gameLastPly()) / 2);
            case BATCH_FENS -> Math.max(1, FenInput.validateBatchFenInput(context.batchInputText()).validRows());
            case EXISTING_FILE -> 6;
        };
    }

    /**
     * Returns the optional positive integer.
     *
     * @param raw raw input text
     * @return optional positive integer
     */
    private static Integer optionalPositiveInteger(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches("[1-9]\\d*")) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Returns the optional positive integer.
     *
     * @param raw raw input text
     * @param fallback default used when input is absent or invalid
     * @return optional positive integer
     */
    private static int optionalPositiveInteger(String raw, int fallback) {
        Integer value = optionalPositiveInteger(raw);
        return value == null ? fallback : value.intValue();
    }

    /**
     * Returns the batch FEN preview.
     *
     * @param context source context
     * @return batch FEN preview text
     */
    private static String batchFenPreview(Context context) {
        String text = context.batchInputText() == null ? "" : context.batchInputText().trim();
        if (text.isEmpty()) {
            return context.gameLastPly() <= 0 ? "batch FENs (empty)" : "game FEN list (" + context.gameLastPly() + " ply)";
        }
        FenInput.Summary scan = FenInput.validateBatchFenInput(text);
        if (scan.hasError()) {
            return scan.rows() + " row" + (scan.rows() == 1 ? "" : "s")
                    + ", issue on line " + scan.firstErrorLine();
        }
        return scan.validRows() + " valid FEN row" + (scan.validRows() == 1 ? "" : "s");
    }

    /**
     * Returns the batch FEN issue.
     *
     * @param context source context
     * @return batch FEN issue text
     */
    private static String batchFenIssue(Context context) {
        String text = context.batchInputText() == null ? "" : context.batchInputText().trim();
        if (text.isEmpty()) {
            return context.gameLastPly() <= 0 ? "Add FEN rows in Batch or play a game line before exporting diagrams."
                    : null;
        }
        FenInput.Summary scan = FenInput.validateBatchFenInput(text);
        return scan.hasError() ? "Batch FEN line " + scan.firstErrorLine() + ": " + scan.firstError() : null;
    }

    /**
     * Returns the path or missing.
     *
     * @param label display label
     * @param value candidate value
     * @return path or missing text
     */
    private static String pathOrMissing(String label, String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? "missing " + label : label + " " + text;
    }

    /**
     * Returns the optional output.
     *
     * @param label display label
     * @param value candidate value
     * @param enabled whether enabled
     * @return optional output text
     */
    private static String optionalOutput(String label, String value, boolean enabled) {
        String text = value == null ? "" : value.trim();
        return enabled && !text.isEmpty() ? "; " + label + " " + text : "";
    }
}
