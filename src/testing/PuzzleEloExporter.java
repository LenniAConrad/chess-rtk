package testing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import application.Config;

/**
 * Manual runner for exporting calibrated Elo-rated puzzle records.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PuzzleEloExporter {

    /**
     * Utility class; prevent instantiation.
     */
    private PuzzleEloExporter() {
        // utility
    }

    /**
     * Runs the exporter.
     *
     * @param args command-line arguments
     * @throws IOException if reading or writing fails
     */
    public static void main(String[] args) throws IOException {
        Arguments parsed = parseArguments(args);
        if (parsed == null) {
            System.err.println("Usage: java -cp out testing.PuzzleEloExporter "
                    + "[--out puzzles.elo.jsonl] [--max-puzzles N] [--threads N] "
                    + "[--ratings-csv scored.csv] <records.json>...");
            System.exit(2);
        }
        Config.reload();
        chess.io.PuzzleEloExporter.Summary summary = parsed.ratingsCsv() == null
                ? chess.io.PuzzleEloExporter.export(
                        parsed.inputs(),
                        parsed.output(),
                        new chess.io.PuzzleEloExporter.Options(Config.getPuzzleVerify(), parsed.maxPuzzles(),
                                parsed.threads()))
                : chess.io.PuzzleEloExporter.exportFromRatingCsv(
                        parsed.inputs(),
                        parsed.output(),
                        parsed.ratingsCsv(),
                        Config.getPuzzleVerify());
        printSummary(parsed.output(), summary);
    }

    /**
     * Parses command-line arguments.
     */
    private static Arguments parseArguments(String[] args) {
        if (args.length == 0) {
            return null;
        }
        Path output = null;
        Path ratingsCsv = null;
        long maxPuzzles = 0L;
        int threads = Runtime.getRuntime().availableProcessors();
        List<Path> inputs = new ArrayList<>();
        Iterator<String> it = List.of(args).iterator();
        while (it.hasNext()) {
            String token = it.next();
            if ("--out".equals(token) || "-o".equals(token)) {
                if (!it.hasNext()) {
                    return null;
                }
                output = Path.of(it.next());
            } else if ("--max-puzzles".equals(token)) {
                if (!it.hasNext()) {
                    return null;
                }
                try {
                    maxPuzzles = Long.parseLong(it.next());
                } catch (NumberFormatException ex) {
                    return null;
                }
            } else if ("--threads".equals(token)) {
                if (!it.hasNext()) {
                    return null;
                }
                try {
                    threads = Integer.parseInt(it.next());
                } catch (NumberFormatException ex) {
                    return null;
                }
            } else if ("--ratings-csv".equals(token)) {
                if (!it.hasNext()) {
                    return null;
                }
                ratingsCsv = Path.of(it.next());
            } else if (token.startsWith("-")) {
                return null;
            } else {
                inputs.add(Path.of(token));
            }
        }
        if (inputs.isEmpty() || threads <= 0) {
            return null;
        }
        Path out = output == null ? defaultOutput(inputs) : output;
        return new Arguments(List.copyOf(inputs), out, Math.max(0L, maxPuzzles), threads, ratingsCsv);
    }

    /**
     * Builds a default output path.
     */
    private static Path defaultOutput(List<Path> inputs) {
        if (inputs.size() != 1) {
            return Path.of("puzzles.elo.jsonl");
        }
        Path input = inputs.get(0);
        Path parent = input.getParent();
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        Path out = Path.of(stem + ".elo-puzzles.jsonl");
        return parent == null ? out : parent.resolve(out);
    }

    /**
     * Prints the export summary.
     */
    private static void printSummary(Path output, chess.io.PuzzleEloExporter.Summary summary) {
        System.out.println("seen: " + summary.seen());
        System.out.println("indexed_puzzles: " + summary.indexedPuzzles());
        System.out.println("written: " + summary.written());
        System.out.println("non_puzzles: " + summary.nonPuzzles());
        System.out.println("skipped: " + summary.skipped());
        System.out.println("invalid: " + summary.invalid());
        System.out.println("truncated_trees: " + summary.truncatedTrees());
        System.out.println("output: " + output);
    }

    /**
     * Parsed command-line arguments.
     */
    private record Arguments(List<Path> inputs, Path output, long maxPuzzles, int threads, Path ratingsCsv) {
    }
}
