package chess.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.puzzle.Difficulty;
import chess.puzzle.PieceIdentityTracker;
import chess.puzzle.Scorer;
import chess.puzzle.Scorer.NodeScore;
import chess.puzzle.Scorer.PuzzleTreeSummary;
import chess.struct.Record;
import chess.tag.Generator;
import chess.uci.Filter;
import utility.Json;

/**
 * Exports verified puzzle records with direct Elo-like difficulty metadata.
 *
 * <p>
 * The exporter scores every verified solver-to-move puzzle position on its own,
 * links explicit continuation trees through record parent positions, then writes
 * JSONL records with fresh {@code META: puzzle_*} tags. Ratings are computed per
 * puzzle and are not remapped by the exported subset.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PuzzleEloExporter {

    /**
     * Large text buffer for JSONL inputs.
     */
    private static final int TEXT_BUFFER_SIZE = 1 << 20;

    /**
     * Maximum explicit solver-position depth to traverse per puzzle.
     */
    private static final int MAX_TREE_SOLVER_DEPTH = 24;

    /**
     * Safety cap for one root tree.
     */
    private static final int MAX_TREE_NODES_PER_ROOT = 100_000;

    /**
     * Current puzzle Elo model identifier.
     */
    private static final String PUZZLE_ELO_MODEL = "crtk-tree-v14-evidence-direct";

    /**
     * Difficulty tag prefixes replaced on export.
     */
    private static final String[] PUZZLE_META_PREFIXES = {
            "META: puzzle_goal=",
            "META: puzzle_rating=",
            "META: puzzle_difficulty=",
            "META: puzzle_difficulty_score=",
            "META: puzzle_variations=",
            "META: puzzle_branch_points=",
            "META: puzzle_solution_plies=",
            "META: puzzle_features=",
            "META: puzzle_tree_nodes=",
            "META: puzzle_root_replies=",
            "META: puzzle_elo_model="
    };

    /**
     * Utility class; prevent instantiation.
     */
    private PuzzleEloExporter() {
        // utility
    }

    /**
     * Export options.
     *
     * @param puzzleVerify verification filter used when a record has no
     *                     {@code kind=puzzle} field
     * @param maxPuzzles maximum number of verified puzzles to score, or 0 for all
     * @param threads tree-scoring worker count
     */
    public record Options(Filter puzzleVerify, long maxPuzzles, int threads) {

        /**
         * Normalizes option values.
         */
        public Options {
            maxPuzzles = Math.max(0L, maxPuzzles);
            threads = Math.max(1, threads);
        }

        /**
         * Creates options with the default worker count.
         *
         * @param puzzleVerify verification filter
         * @param maxPuzzles maximum verified puzzles to score, or 0 for all
         */
        public Options(Filter puzzleVerify, long maxPuzzles) {
            this(puzzleVerify, maxPuzzles, defaultThreads());
        }
    }

    /**
     * Export counters.
     *
     * @param seen input records seen during indexing
     * @param indexedPuzzles verified puzzle records indexed
     * @param written records written to JSONL
     * @param nonPuzzles parsed records rejected by puzzle verification
     * @param skipped parsed records without enough puzzle scoring data
     * @param invalid records that failed to parse
     * @param truncatedTrees root trees stopped by safety caps
     */
    public record Summary(
            long seen,
            long indexedPuzzles,
            long written,
            long nonPuzzles,
            long skipped,
            long invalid,
            long truncatedTrees) {
    }

    /**
     * Exports direct Elo-rated puzzle records.
     *
     * @param inputs input record files
     * @param output output JSONL path
     * @param options export options
     * @return export summary
     * @throws IOException if reading or writing fails
     */
    public static Summary export(List<Path> inputs, Path output, Options options) throws IOException {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must not be empty");
        }
        if (output == null) {
            throw new IllegalArgumentException("output must be non-null");
        }
        Options opts = options == null ? new Options(null, 0L) : options;
        Path spool = temporaryPuzzleSpool(output);
        try {
            CorpusIndex index = indexInputs(inputs, opts, spool);
            List<ScoredPuzzle> scored = index.score();
            Map<Long, Difficulty> difficultyBySignature = difficultyBySignature(scored);
            long written = writeRatedRecords(spool, output, difficultyBySignature);
            MutableSummary stats = index.stats;
            stats.written = written;
            return stats.toSummary();
        } finally {
            Files.deleteIfExists(spool);
        }
    }

    /**
     * Exports puzzle records by reusing a scored puzzle rating CSV.
     *
     * <p>
     * This is a fast path for repeat exports. It streams the source records once,
     * checks the raw {@code position} field against the CSV FEN set before parsing a
     * full {@link Record}, then writes only matching puzzle records with the CSV
     * difficulty tags.
     * </p>
     *
     * @param inputs input record files
     * @param output output JSONL path
     * @param ratingsCsv scored puzzle CSV with the standard difficulty columns
     * @return export summary
     * @throws IOException if reading or writing fails
     */
    public static Summary exportFromRatingCsv(List<Path> inputs, Path output, Path ratingsCsv) throws IOException {
        return exportFromRatingCsv(inputs, output, ratingsCsv, null);
    }

    /**
     * Exports puzzle records by reusing a scored puzzle rating CSV.
     *
     * @param inputs input record files
     * @param output output JSONL path
     * @param ratingsCsv scored puzzle CSV with the standard difficulty columns
     * @param puzzleVerify optional verification filter for records without
     *                     {@code kind=puzzle}
     * @return export summary
     * @throws IOException if reading or writing fails
     */
    public static Summary exportFromRatingCsv(
            List<Path> inputs,
            Path output,
            Path ratingsCsv,
            Filter puzzleVerify) throws IOException {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must not be empty");
        }
        if (output == null) {
            throw new IllegalArgumentException("output must be non-null");
        }
        if (ratingsCsv == null) {
            throw new IllegalArgumentException("ratingsCsv must be non-null");
        }
        Map<String, CsvDifficulty> ratings = readRatingsCsv(ratingsCsv);
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        MutableSummary stats = new MutableSummary();
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (Path input : inputs) {
                streamRecordJson(input, objJson -> writeCsvRatedRecord(objJson, writer, ratings, puzzleVerify, stats));
            }
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
        return stats.toSummary();
    }

    /**
     * Builds the compact puzzle corpus index.
     */
    private static CorpusIndex indexInputs(List<Path> inputs, Options options, Path spool) throws IOException {
        try (BufferedWriter puzzleWriter = Files.newBufferedWriter(spool, StandardCharsets.UTF_8)) {
            CorpusIndex index = new CorpusIndex(options, puzzleWriter);
            for (Path input : inputs) {
                streamRecordJson(input, index::acceptJson);
            }
            return index;
        } catch (StopScanning done) {
            // Reached the requested verified-puzzle cap.
            return done.index;
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    /**
     * Creates a temporary puzzle-only spool near the final output when possible.
     */
    private static Path temporaryPuzzleSpool(Path output) throws IOException {
        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
            return Files.createTempFile(parent, output.getFileName().toString() + ".", ".verified-puzzles.tmp");
        }
        return Files.createTempFile("crtk-puzzle-elo-", ".verified-puzzles.tmp");
    }

    /**
     * Default parallelism for tree scoring.
     */
    private static int defaultThreads() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates the final difficulty lookup for the write pass.
     */
    private static Map<Long, Difficulty> difficultyBySignature(List<ScoredPuzzle> scored) {
        Map<Long, Difficulty> out = new HashMap<>(Math.max(16, scored.size() * 2));
        for (ScoredPuzzle puzzle : scored) {
            out.putIfAbsent(puzzle.positionSignature(), puzzle.difficulty());
        }
        return out;
    }

    /**
     * Writes records that were scored during the index pass.
     */
    private static long writeRatedRecords(
            Path spool,
            Path output,
            Map<Long, Difficulty> difficultyBySignature) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        long[] written = { 0L };
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            streamRecordJson(spool, objJson -> writeRatedRecord(objJson, writer, difficultyBySignature, written));
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
        return written[0];
    }

    /**
     * Writes one rated record when it belongs to the scored puzzle set.
     */
    private static void writeRatedRecord(
            String objJson,
            BufferedWriter writer,
            Map<Long, Difficulty> difficultyBySignature,
            long[] written) {
        Record rec;
        try {
            rec = Record.fromJson(objJson);
        } catch (RuntimeException ex) {
            return;
        }
        if (rec == null || rec.getPosition() == null) {
            return;
        }
        Difficulty difficulty = difficultyBySignature.get(rec.getPosition().signatureCore());
        if (difficulty == null) {
            return;
        }
        try {
            writer.write(withDifficultyTags(rec, difficulty).toJson());
            writer.newLine();
            written[0]++;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Writes one source record when its FEN is present in a ratings CSV.
     */
    private static void writeCsvRatedRecord(
            String objJson,
            BufferedWriter writer,
            Map<String, CsvDifficulty> ratings,
            Filter puzzleVerify,
            MutableSummary stats) {
        stats.seen++;
        String fen = Json.parseStringField(objJson, "position");
        CsvDifficulty difficulty = fen == null ? null : ratings.get(fen);
        if (difficulty == null) {
            stats.nonPuzzles++;
            return;
        }
        Record rec;
        try {
            rec = Record.fromJson(objJson);
        } catch (RuntimeException ex) {
            stats.invalid++;
            return;
        }
        if (rec == null || rec.getPosition() == null) {
            stats.skipped++;
            return;
        }
        if ((puzzleVerify != null || Json.parseStringField(objJson, "kind") != null)
                && !isPuzzle(objJson, rec, puzzleVerify)) {
            stats.nonPuzzles++;
            return;
        }
        try {
            writer.write(withPuzzleTags(rec, difficulty.tags()).toJson());
            writer.newLine();
            stats.indexedPuzzles++;
            stats.written++;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Reads the standard puzzle difficulty CSV into a FEN-keyed tag lookup.
     */
    private static Map<String, CsvDifficulty> readRatingsCsv(Path ratingsCsv) throws IOException {
        Map<String, CsvDifficulty> out = new HashMap<>(1_200_000);
        try (BufferedReader reader = Files.newBufferedReader(ratingsCsv, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !header.startsWith("index,goal,rating,score,")) {
                throw new IOException("not a puzzle difficulty CSV: " + ratingsCsv);
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = parseCsv(line);
                if (fields.size() < 22) {
                    continue;
                }
                CsvDifficulty difficulty = CsvDifficulty.from(fields);
                out.putIfAbsent(difficulty.fen(), difficulty);
            }
        }
        if (out.isEmpty()) {
            throw new IOException("no rating rows in " + ratingsCsv);
        }
        return out;
    }

    /**
     * Parses one RFC4180-style CSV row.
     */
    private static List<String> parseCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    /**
     * Copies a record and replaces stale puzzle difficulty tags.
     */
    private static Record withDifficultyTags(Record rec, Difficulty difficulty) {
        List<String> puzzleTags = new ArrayList<>(difficulty.tags());
        if (difficulty.features() != null) {
            puzzleTags.add("META: puzzle_tree_nodes=" + difficulty.features().recordVariationCount());
            puzzleTags.add("META: puzzle_root_replies=" + difficulty.features().variationCount());
        }
        puzzleTags.add("META: puzzle_elo_model=" + PUZZLE_ELO_MODEL);
        return withPuzzleTags(rec, puzzleTags);
    }

    /**
     * Copies a record and replaces stale puzzle difficulty tags with fresh tags.
     *
     * <p>
     * Older Elo-only puzzle exports can contain nothing except {@code META:
     * puzzle_*} tags. When that happens, enrich the output with the canonical
     * position/analysis tags so the rated JSONL can still be filtered by motif,
     * material, phase, evaluation bucket, and related tag families.
     * </p>
     */
    private static Record withPuzzleTags(Record rec, List<String> puzzleTags) {
        List<String> tags = mergedExportTags(rec, puzzleTags);
        return new Record(
                rec.getCreated(),
                rec.getEngine(),
                rec.getParent() == null ? null : rec.getParent().copy(),
                rec.getPosition() == null ? null : rec.getPosition().copy(),
                rec.getDescription(),
                tags.toArray(String[]::new),
                rec.getAnalysis() == null ? null : rec.getAnalysis().copyOf());
    }

    /**
     * Builds the exported tag list while preserving source tags and de-duplicating
     * generated tags.
     */
    private static List<String> mergedExportTags(Record rec, List<String> puzzleTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        boolean hasSourcePositionTags = addExistingNonPuzzleTags(tags, rec);
        if (!hasSourcePositionTags) {
            addGeneratedPositionTags(tags, rec);
        }
        addTags(tags, puzzleTags, true);
        return new ArrayList<>(tags);
    }

    /**
     * Adds existing source tags except stale puzzle difficulty metadata.
     *
     * @return true when at least one non-puzzle source tag was kept
     */
    private static boolean addExistingNonPuzzleTags(LinkedHashSet<String> tags, Record rec) {
        boolean kept = false;
        for (String tag : rec.getTags()) {
            if (isUsableTag(tag) && !isPuzzleMetaTag(tag)) {
                tags.add(tag);
                kept = true;
            }
        }
        return kept;
    }

    /**
     * Adds canonical position tags for sources that only had puzzle Elo metadata.
     */
    private static void addGeneratedPositionTags(LinkedHashSet<String> tags, Record rec) {
        if (rec == null || rec.getPosition() == null) {
            return;
        }
        try {
            addTags(tags, Generator.tags(rec.getPosition(), rec.getAnalysis()), false);
        } catch (RuntimeException ex) {
            // Keep export streaming even if a single historical position cannot be tagged.
        }
    }

    /**
     * Adds a tag collection, optionally allowing puzzle metadata.
     */
    private static void addTags(LinkedHashSet<String> out, List<String> tags, boolean includePuzzleMeta) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String tag : tags) {
            if (!isUsableTag(tag)) {
                continue;
            }
            if (!includePuzzleMeta && isPuzzleMetaTag(tag)) {
                continue;
            }
            out.add(tag);
        }
    }

    /**
     * Checks whether a tag is worth exporting.
     */
    private static boolean isUsableTag(String tag) {
        return tag != null && !tag.isBlank();
    }

    /**
     * Returns true for stale puzzle metadata that this exporter owns.
     */
    private static boolean isPuzzleMetaTag(String tag) {
        if (tag == null) {
            return false;
        }
        for (String prefix : PUZZLE_META_PREFIXES) {
            if (tag.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Streams raw record JSON objects from JSONL or JSON-array files.
     */
    private static void streamRecordJson(Path input, java.util.function.Consumer<String> consumer) throws IOException {
        if (isJsonArrayFile(input)) {
            Json.streamTopLevelObjects(input, consumer);
            return;
        }
        try (InputStream in = Files.newInputStream(input);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8),
                        TEXT_BUFFER_SIZE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    consumer.accept(trimmed);
                }
            }
        }
    }

    /**
     * Detects whether a file begins with a JSON array.
     */
    private static boolean isJsonArrayFile(Path input) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(input), StandardCharsets.UTF_8),
                TEXT_BUFFER_SIZE)) {
            int c;
            while ((c = reader.read()) != -1) {
                boolean skip = c == '\uFEFF' || Character.isWhitespace(c);
                if (!skip) {
                    return c == '[';
                }
            }
        }
        return false;
    }

    /**
     * Determines whether a raw record should be treated as a verified puzzle.
     */
    private static boolean isPuzzle(String objJson, Record rec, Filter puzzleVerify) {
        String kind = Json.parseStringField(objJson, "kind");
        if (kind != null && !kind.isBlank()) {
            return "puzzle".equalsIgnoreCase(kind);
        }
        return puzzleVerify != null && rec != null && puzzleVerify.apply(rec.getAnalysis());
    }

    /**
     * Converts one verified record into a compact indexed puzzle node.
     */
    private static PuzzleNode puzzleNode(Record rec) {
        try {
            Position position = rec.getPosition();
            short best = rec.getAnalysis().getBestMove();
            Position afterBest = position.copy().play(best);
            NodeScore nodeScore = Scorer.scoreNode(position, rec.getAnalysis());
            long parentSignature = rec.getParent() == null ? Long.MIN_VALUE : rec.getParent().signatureCore();
            return new PuzzleNode(
                    position.toString(),
                    parentSignature,
                    position.signatureCore(),
                    afterBest.signatureCore(),
                    best,
                    nodeScore);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Record-stream index.
     */
    private static final class CorpusIndex {

        /**
         * Verified puzzle roots.
         */
        private final List<PuzzleNode> roots = new ArrayList<>();

        /**
         * Root position signatures already selected for output.
         */
        private final Set<Long> rootSignatures = new HashSet<>();

        /**
         * Verified puzzle records keyed by parent signature.
         */
        private final Map<Long, List<PuzzleNode>> childrenByParent = new HashMap<>();

        /**
         * Export options.
         */
        private final Options options;

        /**
         * Puzzle-only spool writer.
         */
        private final BufferedWriter puzzleWriter;

        /**
         * Running counters.
         */
        private final MutableSummary stats = new MutableSummary();

        /**
         * Creates an index.
         */
        CorpusIndex(Options options, BufferedWriter puzzleWriter) {
            this.options = options;
            this.puzzleWriter = puzzleWriter;
        }

        /**
         * Accepts one raw JSON object.
         */
        void acceptJson(String objJson) {
            stats.seen++;
            Record rec;
            try {
                rec = Record.fromJson(objJson);
            } catch (RuntimeException ex) {
                stats.invalid++;
                return;
            }
            if (rec == null) {
                stats.invalid++;
                return;
            }
            if (rec.getPosition() == null || rec.getAnalysis() == null || rec.getAnalysis().isEmpty()
                    || rec.getAnalysis().getBestMove() == Move.NO_MOVE) {
                stats.skipped++;
                return;
            }
            if (!isPuzzle(objJson, rec, options.puzzleVerify())) {
                stats.nonPuzzles++;
                return;
            }
            PuzzleNode node = puzzleNode(rec);
            if (node == null) {
                stats.skipped++;
                return;
            }
            if (node.parentSignature() != Long.MIN_VALUE) {
                childrenByParent.computeIfAbsent(node.parentSignature(), ignored -> new ArrayList<>()).add(node);
            }
            if (!rootSignatures.add(node.positionSignature())) {
                return;
            }
            roots.add(node);
            spoolPuzzle(objJson);
            stats.indexedPuzzles++;
            if (options.maxPuzzles() > 0L && stats.indexedPuzzles >= options.maxPuzzles()) {
                throw new StopScanning(this);
            }
        }

        /**
         * Scores every root after the parent-child index is complete.
         */
        List<ScoredPuzzle> score() {
            if (options.threads() <= 1 || roots.size() < 2_000) {
                return scoreSequential();
            }
            return scoreParallel();
        }

        /**
         * Writes one verified puzzle record into the temporary puzzle-only spool.
         */
        private void spoolPuzzle(String objJson) {
            try {
                puzzleWriter.write(objJson);
                puzzleWriter.newLine();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        /**
         * Scores trees on the current thread.
         */
        private List<ScoredPuzzle> scoreSequential() {
            List<ScoredPuzzle> out = new ArrayList<>(roots.size());
            long truncated = 0L;
            for (int i = 0; i < roots.size(); i++) {
                ScoredTree scored = scoreRoot(roots.get(i));
                if (scored.truncated()) {
                    truncated++;
                }
                out.add(scored.puzzle());
            }
            stats.truncatedTrees += truncated;
            return out;
        }

        /**
         * Scores independent root trees using worker threads.
         */
        private List<ScoredPuzzle> scoreParallel() {
            int workers = Math.min(options.threads(), roots.size());
            int chunkSize = Math.max(1_000, roots.size() / Math.max(1, workers * 8));
            ExecutorService executor = Executors.newFixedThreadPool(workers);
            List<Future<ScoreChunk>> futures = new ArrayList<>();
            try {
                for (int start = 0; start < roots.size(); start += chunkSize) {
                    int from = start;
                    int to = Math.min(roots.size(), start + chunkSize);
                    futures.add(executor.submit(() -> scoreRange(from, to)));
                }
                List<ScoredPuzzle> out = new ArrayList<>(Collections.nCopies(roots.size(), null));
                long truncated = 0L;
                for (Future<ScoreChunk> future : futures) {
                    ScoreChunk chunk = getScoreChunk(future);
                    truncated += chunk.truncatedTrees();
                    for (int i = 0; i < chunk.puzzles().size(); i++) {
                        out.set(chunk.start() + i, chunk.puzzles().get(i));
                    }
                }
                stats.truncatedTrees += truncated;
                return out;
            } finally {
                executor.shutdownNow();
            }
        }

        /**
         * Scores one contiguous root range.
         */
        private ScoreChunk scoreRange(int start, int end) {
            List<ScoredPuzzle> puzzles = new ArrayList<>(end - start);
            long truncated = 0L;
            for (int i = start; i < end; i++) {
                ScoredTree scored = scoreRoot(roots.get(i));
                if (scored.truncated()) {
                    truncated++;
                }
                puzzles.add(scored.puzzle());
            }
            return new ScoreChunk(start, puzzles, truncated);
        }

        /**
         * Reads one worker result.
         */
        private ScoreChunk getScoreChunk(Future<ScoreChunk> future) {
            try {
                return future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while scoring puzzle trees", ex);
            } catch (ExecutionException ex) {
                throw new IllegalStateException("failed to score puzzle trees", ex.getCause());
            }
        }

        /**
         * Scores one root tree.
         */
        private ScoredTree scoreRoot(PuzzleNode root) {
            TreeBuild tree = buildTree(root);
            Difficulty difficulty = Scorer.score(root.nodeScore(), tree.summary());
            ScoredPuzzle puzzle = new ScoredPuzzle(root.fen(), root.positionSignature(), difficulty);
            return new ScoredTree(puzzle, tree.truncated());
        }

        /**
         * Builds the explicit continuation tree by matching after-best positions to
         * child record parents.
         */
        private TreeBuild buildTree(PuzzleNode root) {
            Position rootPosition = new Position(root.fen());
            PieceIdentityTracker rootIdentities = PieceIdentityTracker.from(rootPosition);
            TreeSummaryBuilder builder = new TreeSummaryBuilder(root.nodeScore(),
                    rootIdentities.movingIdentity(rootPosition, root.solutionMove()));
            Queue<NodeAtDepth> queue = new ArrayDeque<>();
            Set<Long> seen = new HashSet<>();
            queue.add(new NodeAtDepth(root, 1, rootIdentities));
            seen.add(root.positionSignature());
            boolean truncated = false;
            while (!queue.isEmpty()) {
                NodeAtDepth current = queue.remove();
                Position currentPosition = new Position(current.node().fen());
                Position afterSolution = playOrNull(currentPosition, current.node().solutionMove());
                PieceIdentityTracker afterSolutionIdentities = afterSolution == null
                        ? current.identities()
                        : current.identities().after(currentPosition, current.node().solutionMove(), afterSolution);
                List<PuzzleNode> children = uniqueChildren(childrenByParent.get(current.node().afterBestSignature()));
                if (current.depth() == 1) {
                    builder.setRootReplyCount(children.size());
                }
                if (children.size() > 1) {
                    builder.addBranch(children.size());
                }
                int childDepth = current.depth() + 1;
                if (childDepth > MAX_TREE_SOLVER_DEPTH) {
                    truncated |= !children.isEmpty();
                    continue;
                }
                for (PuzzleNode child : children) {
                    if (!seen.add(child.positionSignature())) {
                        continue;
                    }
                    if (builder.nodeCount >= MAX_TREE_NODES_PER_ROOT) {
                        truncated = true;
                        queue.clear();
                        break;
                    }
                    Position childPosition = new Position(child.fen());
                    short reply = afterSolution == null
                            ? Move.NO_MOVE
                            : replyMove(afterSolution, child.positionSignature());
                    PieceIdentityTracker childIdentities = reply == Move.NO_MOVE
                            ? PieceIdentityTracker.from(childPosition)
                            : afterSolutionIdentities.after(afterSolution, reply, childPosition);
                    builder.addNode(child.nodeScore(), childDepth,
                            childIdentities.movingIdentity(childPosition, child.solutionMove()));
                    queue.add(new NodeAtDepth(child, childDepth, childIdentities));
                }
            }
            return new TreeBuild(builder.build(), truncated);
        }
    }

    /**
     * Plays a move defensively.
     *
     * @param position source position
     * @param move encoded move
     * @return resulting position, or null when the move cannot be applied
     */
    private static Position playOrNull(Position position, short move) {
        if (position == null || move == Move.NO_MOVE) {
            return null;
        }
        try {
            return position.copy().play(move);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Finds the opponent reply that reaches a child solver position.
     *
     * @param parentAfterSolution position after the parent solver move
     * @param childSignature child position signature
     * @return opponent reply move, or {@link Move#NO_MOVE}
     */
    private static short replyMove(Position parentAfterSolution, long childSignature) {
        if (parentAfterSolution == null) {
            return Move.NO_MOVE;
        }
        MoveList legal = parentAfterSolution.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.get(i);
            Position candidate = playOrNull(parentAfterSolution, move);
            if (candidate != null && candidate.signatureCore() == childSignature) {
                return move;
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Deduplicates child records by analyzed position signature.
     */
    private static List<PuzzleNode> uniqueChildren(List<PuzzleNode> children) {
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        if (children.size() == 1) {
            return children;
        }
        List<PuzzleNode> unique = new ArrayList<>(children.size());
        Set<Long> seen = new HashSet<>();
        for (PuzzleNode child : children) {
            if (seen.add(child.positionSignature())) {
                unique.add(child);
            }
        }
        return unique;
    }

    /**
     * Difficulty row read from the scorer CSV.
     */
    private record CsvDifficulty(
            String fen,
            String goal,
            int rating,
            double score,
            String label,
            int solutionPlies,
            int rootReplyCount,
            int treeNodeCount,
            int branchPointCount,
            String features) {

        /**
         * Builds a row from standard CSV columns.
         */
        static CsvDifficulty from(List<String> fields) {
            return new CsvDifficulty(
                    fields.get(21),
                    fields.get(1),
                    parseIntField(fields.get(2), 0),
                    parseDoubleField(fields.get(3), 0.0),
                    fields.get(5),
                    parseIntField(fields.get(16), 1),
                    parseIntField(fields.get(17), 1),
                    parseIntField(fields.get(18), 1),
                    parseIntField(fields.get(19), 0),
                    fields.get(20));
        }

        /**
         * Converts the CSV row to canonical puzzle metadata tags.
         */
        List<String> tags() {
            List<String> tags = new ArrayList<>(10);
            tags.add("META: puzzle_goal=" + emptyToDefault(goal, "unknown"));
            tags.add("META: puzzle_rating=" + rating);
            tags.add("META: puzzle_difficulty=" + emptyToDefault(label, "medium"));
            tags.add("META: puzzle_difficulty_score=" + String.format(Locale.ROOT, "%.2f", score));
            tags.add("META: puzzle_variations=" + rootReplyCount);
            tags.add("META: puzzle_branch_points=" + branchPointCount);
            tags.add("META: puzzle_solution_plies=" + solutionPlies);
            if (features != null && !features.isBlank()) {
                tags.add("META: puzzle_features=\"" + features + "\"");
            }
            tags.add("META: puzzle_tree_nodes=" + treeNodeCount);
            tags.add("META: puzzle_root_replies=" + rootReplyCount);
            tags.add("META: puzzle_elo_model=" + PUZZLE_ELO_MODEL);
            return tags;
        }

        /**
         * Parses an integer CSV field with a fallback.
         */
        private static int parseIntField(String value, int fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }

        /**
         * Parses a floating-point CSV field with a fallback.
         */
        private static double parseDoubleField(String value, double fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }

        /**
         * Normalizes blank metadata values.
         */
        private static String emptyToDefault(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    /**
     * Compact indexed puzzle record.
     */
    private record PuzzleNode(
            String fen,
            long parentSignature,
            long positionSignature,
            long afterBestSignature,
            short solutionMove,
            NodeScore nodeScore) {
    }

    /**
     * Scored puzzle row.
     */
    private record ScoredPuzzle(String fen, long positionSignature, Difficulty difficulty) {
    }

    /**
     * One scored tree plus truncation flag.
     */
    private record ScoredTree(ScoredPuzzle puzzle, boolean truncated) {
    }

    /**
     * Parallel scoring range result.
     */
    private record ScoreChunk(int start, List<ScoredPuzzle> puzzles, long truncatedTrees) {
    }

    /**
     * Queue item for tree traversal.
     */
    private record NodeAtDepth(PuzzleNode node, int depth, PieceIdentityTracker identities) {
    }

    /**
     * Tree traversal result.
     */
    private record TreeBuild(PuzzleTreeSummary summary, boolean truncated) {
    }

    /**
     * Mutable explicit-tree summary builder.
     */
    private static final class TreeSummaryBuilder {

        /**
         * Count of solver positions included in the tree.
         */
        private int nodeCount;

        /**
         * Child replies immediately after the root key.
         */
        private int rootReplyCount = 1;

        /**
         * Branching parent count.
         */
        private int branchPointCount;

        /**
         * Maximum solver depth reached.
         */
        private int maxDepth;

        /**
         * Harmonic weighted non-root raw score numerator.
         */
        private double continuationWeightedRaw;

        /**
         * Harmonic weighted non-root denominator.
         */
        private double continuationWeight;

        /**
         * Harmonic depth mass over every non-root solver node.
         */
        private double continuationDepthLoad;

        /**
         * Hardest non-root node raw score.
         */
        private double continuationPeakRaw;

        /**
         * Sublinear branch burden.
         */
        private double branchLoad;

        /**
         * Bit mask of moving piece types.
         */
        private int pieceTypeMask;

        /**
         * Bit mask of non-pawn, non-king moving piece types.
         */
        private int nonPawnNonKingPieceTypeMask;

        /**
         * Stable moving-piece identities tracked through the explicit tree.
         */
        private final Set<Long> pieceIdentities = new HashSet<>();

        /**
         * Solver pawn moves.
         */
        private int pawnMoveCount;

        /**
         * Solver king moves.
         */
        private int kingMoveCount;

        /**
         * Move counts per stable moving piece.
         */
        private final Map<Long, Integer> pieceIdentityMoveCounts = new HashMap<>();

        /**
         * Non-checking non-capturing solver moves.
         */
        private int nonforcingMoveCount;

        /**
         * Underpromotion solver moves.
         */
        private int underpromotionCount;

        /**
         * En-passant solver moves.
         */
        private int enPassantCount;

        /**
         * Castling solver moves.
         */
        private int castleCount;

        /**
         * Creates a tree starting at the root.
         */
        TreeSummaryBuilder(NodeScore root, long rootIdentity) {
            addNode(root, 1, rootIdentity);
        }

        /**
         * Adds one solver node at a measured depth.
         */
        void addNode(NodeScore node, int depth, long pieceIdentity) {
            nodeCount++;
            maxDepth = Math.max(maxDepth, depth);
            if (depth > 1) {
                double weight = 1.0 / depth;
                continuationWeightedRaw += node.rawScore() * weight;
                continuationWeight += weight;
                continuationDepthLoad += weight;
                continuationPeakRaw = Math.max(continuationPeakRaw, node.rawScore());
            }
            if (node.pieceType() > 0) {
                pieceTypeMask |= 1 << node.pieceType();
            }
            if (node.pieceType() > chess.core.Piece.PAWN && node.pieceType() < chess.core.Piece.KING) {
                nonPawnNonKingPieceTypeMask |= 1 << node.pieceType();
            }
            if (node.pieceType() == chess.core.Piece.PAWN) {
                pawnMoveCount++;
            }
            if (node.pieceType() == chess.core.Piece.KING) {
                kingMoveCount++;
            }
            if (pieceIdentity != PieceIdentityTracker.NO_IDENTITY) {
                pieceIdentities.add(pieceIdentity);
                pieceIdentityMoveCounts.merge(pieceIdentity, 1, Integer::sum);
            }
            if (node.keyQuiet()) {
                nonforcingMoveCount++;
            }
            if (node.keyUnderpromotion()) {
                underpromotionCount++;
            }
            if (node.keyEnPassant()) {
                enPassantCount++;
            }
            if (node.keyCastle()) {
                castleCount++;
            }
        }

        /**
         * Records root reply count.
         */
        void setRootReplyCount(int count) {
            rootReplyCount = Math.max(1, count);
        }

        /**
         * Records one branching parent.
         */
        void addBranch(int childCount) {
            if (childCount <= 1) {
                return;
            }
            branchPointCount++;
            branchLoad += Math.log1p(childCount - 1.0);
        }

        /**
         * Builds the immutable scorer summary.
         */
        PuzzleTreeSummary build() {
            double continuationAverage = continuationWeight <= 0.0 ? 0.0 : continuationWeightedRaw / continuationWeight;
            double continuation = continuationWeight <= 0.0 ? 0.0
                    : 0.70 * continuationAverage + 0.30 * continuationPeakRaw;
            int dominantPieceMoves = 0;
            for (int count : pieceIdentityMoveCounts.values()) {
                dominantPieceMoves = Math.max(dominantPieceMoves, count);
            }
            double dominantPieceMoveShare = nodeCount <= 0 ? 0.0 : dominantPieceMoves / (double) nodeCount;
            return new PuzzleTreeSummary(
                    nodeCount,
                    rootReplyCount,
                    branchPointCount,
                    Math.max(1, maxDepth),
                    continuation,
                    continuationDepthLoad,
                    branchLoad,
                    Integer.bitCount(pieceTypeMask),
                    Integer.bitCount(nonPawnNonKingPieceTypeMask),
                    pieceIdentities.size(),
                    pawnMoveCount,
                    kingMoveCount,
                    dominantPieceMoveShare,
                    nonforcingMoveCount,
                    underpromotionCount,
                    enPassantCount,
                    castleCount);
        }
    }

    /**
     * Running counters.
     */
    private static final class MutableSummary {

        /**
         * Input records seen during indexing.
         */
        private long seen;

        /**
         * Verified puzzle records indexed.
         */
        private long indexedPuzzles;

        /**
         * Records written to output.
         */
        private long written;

        /**
         * Parsed records rejected by puzzle verification.
         */
        private long nonPuzzles;

        /**
         * Parsed records without enough puzzle scoring data.
         */
        private long skipped;

        /**
         * Records that failed to parse.
         */
        private long invalid;

        /**
         * Root trees stopped by safety caps.
         */
        private long truncatedTrees;

        /**
         * Converts to immutable summary.
         */
        Summary toSummary() {
            return new Summary(seen, indexedPuzzles, written, nonPuzzles, skipped, invalid, truncatedTrees);
        }
    }

    /**
     * Internal control-flow signal used to stop scanning after a sample cap.
     */
    private static final class StopScanning extends RuntimeException {

        /**
         * Avoids warning noise for this local control-flow exception.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Index accumulated before stopping.
         */
        private final CorpusIndex index;

        /**
         * Creates the stop signal.
         */
        StopScanning(CorpusIndex index) {
            this.index = index;
        }
    }
}
