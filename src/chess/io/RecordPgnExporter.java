package chess.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

import application.console.Bar;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.debug.LogService;
import chess.struct.Game;
import chess.struct.Record;
import chess.uci.Evaluation;
import chess.uci.Output;
import utility.Json;

/**
 * Exports a {@code .record} JSON array into PGN by linking records through their
 * {@code parent} and {@code position} fields.
 *
 * <p>
 * This class contains the PGN-specific conversion logic that was previously in
 * {@link Converter}. It is intentionally focused on the record graph construction
 * and PGN game building; other exports remain in {@link Converter}.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class RecordPgnExporter {

    /**
     * Base score used when converting mate distances into sortable evaluation values.
     */
    private static final int MATE_SCORE_BASE = 100_000;

    /**
     * Non-instantiable utility.
     */
    private RecordPgnExporter() {
        // prevents instantiation
    }

    /**
     * Converts a {@code .record} JSON array/JSONL file into PGN games by linking records
     * via their {@code parent} and {@code position} FENs.
     *
     * <p>
     * The exporter connects records directly when a record's {@code parent} equals
     * another record's {@code position}, and also bridges positions by generating
     * legal subpositions to find matching {@code parent} nodes. This yields
     * two-ply links for mining outputs where the intermediate parent is not
     * itself stored as a record position. If a leaf record has analysis data,
     * its best move is appended as a final ply.
     * </p>
     *
     * @param recordFile input JSON (array or JSONL) path.
     * @param pgnFile    output PGN path (must not be null).
     * @throws IllegalArgumentException if {@code recordFile} or {@code pgnFile} is null.
     */
    public static void export(Path recordFile, Path pgnFile) {
        exportFiltered(recordFile, pgnFile, null);
    }

    /**
     * Converts a {@code .record} JSON array/JSONL file into PGN games while filtering
     * records by their raw JSON payload.
     *
     * @param recordFile        input JSON (array or JSONL) path.
     * @param pgnFile           output PGN path (must not be null).
     * @param includeRecordJson predicate to decide whether a record should be included; {@code null} = include all.
     * @throws IllegalArgumentException if {@code recordFile} or {@code pgnFile} is null.
     */
    public static void exportFiltered(Path recordFile, Path pgnFile, Predicate<String> includeRecordJson) {
        if (recordFile == null) {
            throw new IllegalArgumentException("recordfile is null");
        }
        if (pgnFile == null) {
            throw new IllegalArgumentException("pgnFile is null");
        }

        final AtomicLong ok = new AtomicLong();
        final AtomicLong bad = new AtomicLong();
        final AtomicLong badEdges = new AtomicLong();
        final Predicate<String> include = (includeRecordJson == null)
                ? json -> true
                : includeRecordJson;

        LogService.info(String.format(
                "Converting records to PGN '%s' to '%s'.",
                recordFile, pgnFile));

        try {
            long totalRecords = countRecords(recordFile);
            IndexData index = indexRecords(recordFile, ok, bad, badEdges, totalRecords, include);
            if (index.positionsBySig.isEmpty()) {
                LogService.info(String.format(
                        "No records parsed from '%s'; skipping PGN output '%s'.",
                        recordFile, pgnFile));
                return;
            }

            List<String> startSigs = computeStartSignatures(index.positionsBySig.keySet(), index.incoming);
            if (startSigs.isEmpty()) {
                LogService.info(String.format(
                        "No root positions found in '%s'; skipping PGN output '%s'.",
                        recordFile, pgnFile));
                return;
            }

            Map<String, Integer> lineLengthBySig = computeLineLengths(index.positionsBySig.keySet(), index.adjacency,
                    index.bestMoveBySig);
            PgnContext ctx = new PgnContext(
                    index.adjacency,
                    index.bestMoveBySig,
                    index.descriptionBySig,
                    index.evalScoreBySig,
                    lineLengthBySig);
            long gamesWritten = writePgnOutput(pgnFile, startSigs, index.positionsBySig, ctx, recordFile);
            if (gamesWritten < 0) {
                return;
            }
            if (gamesWritten == 0) {
                LogService.info(String.format(
                        "No PGN games produced from '%s'; skipping output '%s'.",
                        recordFile, pgnFile));
                return;
            }

            LogService.info(String.format(
                    "Completed record to PGN conversion '%s' to '%s' and wrote %d games (bad records=%d, bad edges=%d).",
                    recordFile, pgnFile, gamesWritten, bad.get(), badEdges.get()));
        } catch (Exception e) {
            LogService.error(
                    e,
                    String.format(
                            "I/O error during record to PGN conversion '%s' to '%s'.",
                            recordFile, pgnFile));
        }
    }

    /**
     * Writes parsed games to {@code pgnFile}, logging failures that mention the source.
     *
     * @param pgnFile       destination PGN path.
     * @param startSigs     root signatures in output order.
     * @param positionsBySig map of signature to starting FEN.
     * @param ctx           shared graph context.
     * @param recordFile    source record file for log context.
     * @return number of games written, or {@code -1} on failure.
     */
    private static long writePgnOutput(
            Path pgnFile,
            List<String> startSigs,
            Map<String, String> positionsBySig,
            PgnContext ctx,
            Path recordFile) {
        long written = 0;
        try (BufferedWriter out = Files.newBufferedWriter(
                pgnFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            for (String sig : startSigs) {
                String fen = positionsBySig.get(sig);
                String pgn = buildPgnForStart(sig, fen, ctx);
                if (pgn == null || pgn.isEmpty()) {
                    continue;
                }
                if (written > 0) {
                    out.newLine();
                    out.newLine();
                }
                out.write(pgn);
                written++;
            }
        } catch (IOException e) {
            LogService.error(
                    e,
                    String.format(
                            "Failed to write PGN output '%s' from '%s'.",
                            pgnFile, recordFile));
            return -1;
        }
        return written;
    }

    /**
     * Builds the PGN for a single start signature when valid.
     *
     * <p>
     * The method walks the directed acyclic graph anchored at {@code sig}, collects
     * the corresponding moves, inserts comments for alternating continuations, and
     * flattens the result into a single PGN game string.
     * </p>
     *
     * @param sig start signature (derived from a position FEN).
     * @param fen starting FEN string.
     * @param ctx shared PGN context holding adjacency and evaluation metadata.
     * @return serialized PGN or {@code null} when the line cannot be rendered.
     */
    private static String buildPgnForStart(String sig, String fen, PgnContext ctx) {
        if (fen == null || fen.isEmpty()) {
            return null;
        }
        Game game = buildGameWithVariations(sig, new Position(fen), ctx);
        if (game == null) {
            return null;
        }
        return chess.struct.Pgn.toPgn(game);
    }

    /**
     * Streams all records and builds the adjacency/counter maps needed to construct
     * PGN games later.
     *
     * <p>
     * The process has two phases: first it streams the JSON and extracts positions,
     * best-move metadata, and child signatures. After that it attempts to link both
     * direct edges (parent⇒child) and bridged links when the intermediate parent is
     * not stored explicitly.
     * </p>
     *
     * @param recordFile source JSON path.
     * @param ok         counter for valid records.
     * @param bad        counter for skipped records.
     * @param badEdges   counter for missing SAN links.
     * @param totalRecords estimated record count for progress reporting
     * @param include    predicate to decide whether to include a record
     * @return {@link IndexData} holding map views used by {@link #buildPgnForStart}
     * @throws IOException if the stream fails.
     */
    private static IndexData indexRecords(
            Path recordFile,
            AtomicLong ok,
            AtomicLong bad,
            AtomicLong badEdges,
            long totalRecords,
            Predicate<String> include) throws IOException {
        final Map<String, String> positionsBySig = new LinkedHashMap<>();
        final Map<String, List<ChildInfo>> childrenByParentSig = new HashMap<>();
        final Map<String, Set<String>> directChildren = new HashMap<>();
        final Map<String, Set<EdgeKey>> edgeKeysByFrom = new HashMap<>();
        final Map<String, String> bestMoveBySig = new HashMap<>();
        final Map<String, String> descriptionBySig = new HashMap<>();
        final Map<String, Integer> evalScoreBySig = new HashMap<>();

        Bar indexBar = progressBar(totalRecords, "Indexing records");
        IndexMaps indexMaps = new IndexMaps(positionsBySig, childrenByParentSig, bestMoveBySig, descriptionBySig,
                evalScoreBySig);
        IndexContext indexCtx = new IndexContext(ok, bad, indexBar, indexMaps);
        try {
            streamRecordObjects(recordFile, objJson -> {
                if (!include.test(objJson)) {
                    if (indexBar != null) {
                        indexBar.step();
                    }
                    return;
                }
                handleIndexRecord(objJson, indexCtx);
            });
        } finally {
            if (indexBar != null) {
                indexBar.finish();
            }
        }

        final Map<String, List<Edge>> adjacency = new LinkedHashMap<>();
        final Set<String> incoming = new HashSet<>();
        addDirectEdges(childrenByParentSig, positionsBySig, adjacency, incoming, badEdges, directChildren,
                edgeKeysByFrom);
        Bar bridgeBar = progressBar(totalRecords, "Linking records");
        BridgeContext bridgeCtx = new BridgeContext(adjacency, incoming, badEdges, directChildren, edgeKeysByFrom,
                bridgeBar);
        addBridgedEdges(recordFile, childrenByParentSig, bridgeCtx, include);

        return new IndexData(positionsBySig, adjacency, incoming, bestMoveBySig, descriptionBySig, evalScoreBySig);
    }

    /**
     * Parses and indexes one JSON record, updating counters and the progress bar.
     *
     * <p>
     * The method tolerates invalid JSON by skipping records that do not parse or
     * lack a {@code position}. Valid entries are persisted into the various maps
     * that later produce PGN edges.
     * </p>
     *
     * @param objJson raw JSON object string.
     * @param ctx     indexing context with counters and map views.
     */
    private static void handleIndexRecord(String objJson, IndexContext ctx) {
        Record rec = safeParseRecord(objJson);
        if (rec == null || rec.getPosition() == null) {
            ctx.bad.incrementAndGet();
            if (ctx.indexBar != null) {
                ctx.indexBar.step();
            }
            return;
        }
        ctx.ok.incrementAndGet();
        indexRecordData(rec, ctx.positionsBySig, ctx.childrenByParentSig, ctx.bestMoveBySig, ctx.descriptionBySig,
                ctx.evalScoreBySig);
        if (ctx.indexBar != null) {
            ctx.indexBar.step();
        }
    }

    /**
     * Shared state for indexing streamed records.
     */
    private static final class IndexContext {

        /**
         * Counter for successfully parsed records.
         */
        private final AtomicLong ok;

        /**
         * Counter for records that are skipped or invalid.
         */
        private final AtomicLong bad;

        /**
         * Progress bar for indexing.
         */
        private final Bar indexBar;

        /**
         * Map of signature to FEN string.
         */
        private final Map<String, String> positionsBySig;

        /**
         * Map of parent signature to its children.
         */
        private final Map<String, List<ChildInfo>> childrenByParentSig;

        /**
         * Map of signature to best move in SAN.
         */
        private final Map<String, String> bestMoveBySig;

        /**
         * Map of signature to human-readable description.
         */
        private final Map<String, String> descriptionBySig;

        /**
         * Map of signature to evaluation score.
         */
        private final Map<String, Integer> evalScoreBySig;

        /**
         * Creates a context wrapper for indexing counters, progress bar, and map views.
         *
         * @param ok       counter for successfully parsed records.
         * @param bad      counter for skipped or invalid records.
         * @param indexBar progress bar for indexing, may be null.
         * @param maps     shared index maps backing this context.
         */
        private IndexContext(
                AtomicLong ok,
                AtomicLong bad,
                Bar indexBar,
                IndexMaps maps) {
            this.ok = ok;
            this.bad = bad;
            this.indexBar = indexBar;
            this.positionsBySig = maps.positionsBySig;
            this.childrenByParentSig = maps.childrenByParentSig;
            this.bestMoveBySig = maps.bestMoveBySig;
            this.descriptionBySig = maps.descriptionBySig;
            this.evalScoreBySig = maps.evalScoreBySig;
        }
    }

    /**
     * Container for shared index maps.
     */
    private static final class IndexMaps {

        /**
         * Map of signature to FEN string.
         */
        private final Map<String, String> positionsBySig;

        /**
         * Map of parent signature to its children.
         */
        private final Map<String, List<ChildInfo>> childrenByParentSig;

        /**
         * Map of signature to best move in SAN.
         */
        private final Map<String, String> bestMoveBySig;

        /**
         * Map of signature to descriptive text.
         */
        private final Map<String, String> descriptionBySig;

        /**
         * Map of signature to evaluation score.
         */
        private final Map<String, Integer> evalScoreBySig;

        /**
         * Creates a container for shared index maps.
         *
         * @param positionsBySig    map of signature to FEN string
         * @param childrenByParentSig map of parent signature to child descriptors
         * @param bestMoveBySig     map of signature to best-move SAN
         * @param descriptionBySig  map of signature to descriptive text
         * @param evalScoreBySig    map of signature to evaluation score
         */
        private IndexMaps(
                Map<String, String> positionsBySig,
                Map<String, List<ChildInfo>> childrenByParentSig,
                Map<String, String> bestMoveBySig,
                Map<String, String> descriptionBySig,
                Map<String, Integer> evalScoreBySig) {
            this.positionsBySig = positionsBySig;
            this.childrenByParentSig = childrenByParentSig;
            this.bestMoveBySig = bestMoveBySig;
            this.descriptionBySig = descriptionBySig;
            this.evalScoreBySig = evalScoreBySig;
        }
    }

    /**
     * Extracts record data and stores it in the provided index maps.
     *
     * <p>
     * Adds the record's position signature, best move, description, and evaluation
     * score if they are present. It also enqueues the parent→child relationship
     * and caches the SAN string describing that edge for later PGN generation.
     * </p>
     *
     * @param rec               record to index.
     * @param positionsBySig    map for signature to FEN.
     * @param childrenByParentSig map of parent signature to children.
     * @param bestMoveBySig     map of best move SAN by signature.
     * @param descriptionBySig  map of description text by signature.
     * @param evalScoreBySig    map of evaluation score by signature.
     */
    private static void indexRecordData(
            Record rec,
            Map<String, String> positionsBySig,
            Map<String, List<ChildInfo>> childrenByParentSig,
            Map<String, String> bestMoveBySig,
            Map<String, String> descriptionBySig,
            Map<String, Integer> evalScoreBySig) {
        Position position = rec.getPosition();
        String posSig = fenSignature(position);
        positionsBySig.putIfAbsent(posSig, position.toString());

        String bestMoveSan = formatBestMoveSan(position, rec.getAnalysis().getBestMove());
        if (bestMoveSan != null && !bestMoveSan.isEmpty()) {
            bestMoveBySig.putIfAbsent(posSig, bestMoveSan);
        }

        String description = rec.getDescription();
        if (description != null) {
            description = description.trim();
        }
        if (description != null && !description.isEmpty()) {
            descriptionBySig.putIfAbsent(posSig, description);
        }

        Integer evalScore = evalScoreFromRecord(rec);
        if (evalScore != null) {
            evalScoreBySig.putIfAbsent(posSig, evalScore);
        }

        Position parent = rec.getParent();
        if (parent != null) {
            String parentSig = fenSignature(parent);
            String parentToPositionSan = sanFromEdge(parent, position);
            childrenByParentSig
                    .computeIfAbsent(parentSig, k -> new ArrayList<>())
                    .add(new ChildInfo(posSig, parentToPositionSan));
        }
    }

    /**
     * Parses a record safely, treating malformed JSON or invalid FEN as null.
     *
     * @param objJson raw JSON object string.
     * @return parsed {@link Record}, or {@code null} if invalid.
     */
    private static Record safeParseRecord(String objJson) {
        try {
            return Record.fromJson(objJson);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }

    /**
     * Adds edges where the parent position is explicitly stored as another record.
     *
     * @param childrenByParentSig child nodes grouped by parent signature.
     * @param positionsBySig      positions available in the dataset.
     * @param adjacency           adjacency list under construction.
     * @param incoming            set of signatures with incoming edges.
     * @param badEdges            counter incremented when edge data is missing.
     */
    private static void addDirectEdges(
            Map<String, List<ChildInfo>> childrenByParentSig,
            Map<String, String> positionsBySig,
            Map<String, List<Edge>> adjacency,
            Set<String> incoming,
            AtomicLong badEdges,
            Map<String, Set<String>> directChildren,
            Map<String, Set<EdgeKey>> edgeKeysByFrom) {
        for (Map.Entry<String, List<ChildInfo>> entry : childrenByParentSig.entrySet()) {
            String parentSig = entry.getKey();
            if (!positionsBySig.containsKey(parentSig)) {
                continue;
            }
            for (ChildInfo child : entry.getValue()) {
                if (child.parentToPositionSan == null) {
                    badEdges.incrementAndGet();
                    continue;
                }
                directChildren
                        .computeIfAbsent(parentSig, k -> new HashSet<>())
                        .add(child.positionSig);
                addEdge(adjacency, edgeKeysByFrom, parentSig,
                        new Edge(child.positionSig, new String[] { child.parentToPositionSan }));
                incoming.add(child.positionSig);
            }
        }
    }

    /**
     * Generates synthetic connections by exploring legal moves from recorded nodes.
     *
     * @param recordFile          source JSON path.
     * @param childrenByParentSig child nodes grouped by parent signatures.
     * @param adjacency           adjacency list being filled.
     * @param incoming            set of signatures with incoming edges.
     * @param badEdges            counter for invalid child references.
     * @param include             predicate to decide whether to include a record
     * @throws IOException if the stream fails.
     */
    private static void addBridgedEdges(
            Path recordFile,
            Map<String, List<ChildInfo>> childrenByParentSig,
            BridgeContext ctx,
            Predicate<String> include) throws IOException {
        try {
            streamRecordObjects(recordFile, objJson -> {
                if (include.test(objJson)) {
                    processBridgedRecord(objJson, childrenByParentSig, ctx);
                }
                stepBridgeBar(ctx);
            });
        } finally {
            finishBridgeBar(ctx);
        }
    }

    /**
     * Processes a single record for bridge edges by exploring its legal moves.
     *
     * @param objJson           raw JSON object.
     * @param childrenByParentSig child nodes grouped by parent signatures.
     * @param ctx               bridge context for edge creation.
     */
    private static void processBridgedRecord(
            String objJson,
            Map<String, List<ChildInfo>> childrenByParentSig,
            BridgeContext ctx) {
        Record rec = safeParseRecord(objJson);
        if (rec == null) {
            return;
        }
        Position pos = rec.getPosition();
        if (pos == null) {
            return;
        }
        MoveList moves = pos.getMoves();
        if (moves == null || moves.size() == 0) {
            return;
        }
        String fromSig = fenSignature(pos);
        Set<String> directKids = ctx.directChildren.get(fromSig);
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            Position next = pos.copyOf().play(move);
            String nextSig = fenSignature(next);
            List<ChildInfo> kids = getBridgeKids(childrenByParentSig, directKids, nextSig);
            if (kids == null) {
                continue;
            }
            String san = SAN.toAlgebraic(pos, move);
            addBridgedEdgesForKids(fromSig, san, kids, ctx);
        }
    }

    /**
     * Retrieves matching child nodes while excluding direct children already linked.
     *
     * @param childrenByParentSig map of parent signature to children.
     * @param directKids          direct children of the current node.
     * @param nextSig             signature to match.
     * @return list of child info entries or an empty list when none should be bridged.
     */
    private static List<ChildInfo> getBridgeKids(
            Map<String, List<ChildInfo>> childrenByParentSig,
            Set<String> directKids,
            String nextSig) {
        if (directKids != null && directKids.contains(nextSig)) {
            return Collections.emptyList();
        }
        List<ChildInfo> kids = childrenByParentSig.get(nextSig);
        if (kids == null || kids.isEmpty()) {
            return Collections.emptyList();
        }
        return kids;
    }

    /**
     * Advances the bridge progress bar by one step if present.
     *
     * @param ctx bridge context holding the bar.
     */
    private static void stepBridgeBar(BridgeContext ctx) {
        if (ctx.bridgeBar != null) {
            ctx.bridgeBar.step();
        }
    }

    /**
     * Completes the bridge progress bar if present.
     *
     * @param ctx bridge context holding the bar.
     */
    private static void finishBridgeBar(BridgeContext ctx) {
        if (ctx.bridgeBar != null) {
            ctx.bridgeBar.finish();
        }
    }

    /**
     * Adds edges for matching child nodes of a move.
     *
     * @param fromSig   starting position signature.
     * @param san       move SAN leading to the child.
     * @param kids      child nodes sharing the same parent.
     * @param adjacency adjacency list being filled.
     * @param incoming  set of incoming signatures.
     * @param badEdges  counter updated when SAN information is missing.
     */
    private static void addBridgedEdgesForKids(
            String fromSig,
            String san,
            List<ChildInfo> kids,
            BridgeContext ctx) {
        for (ChildInfo kid : kids) {
            if (kid.parentToPositionSan == null) {
                ctx.badEdges.incrementAndGet();
                continue;
            }
            addEdge(ctx.adjacency, ctx.edgeKeysByFrom, fromSig,
                    new Edge(kid.positionSig, new String[] { san, kid.parentToPositionSan }));
            ctx.incoming.add(kid.positionSig);
        }
    }

    /**
     * Determines which signatures lack incoming edges and therefore form roots.
     *
     * @param signatures signatures available in the dataset.
     * @param incoming   signatures that have incoming links.
     * @return candidate root signatures; falls back to all signatures when none are isolated.
     */
    private static List<String> computeStartSignatures(
            Set<String> signatures,
            Set<String> incoming) {
        final List<String> startSigs = new ArrayList<>();
        for (String posSig : signatures) {
            if (!incoming.contains(posSig)) {
                startSigs.add(posSig);
            }
        }
        if (startSigs.isEmpty()) {
            startSigs.addAll(signatures);
        }
        return startSigs;
    }

    /**
     * Computes the maximum continuation length for each signature.
     *
     * @param signatures    signatures to seed the search.
     * @param adjacency     adjacency list describing subsequent moves.
     * @param bestMoveBySig map of recorded best moves.
     * @return map of signature to best continuation length.
     */
    private static Map<String, Integer> computeLineLengths(
            Set<String> signatures,
            Map<String, List<Edge>> adjacency,
            Map<String, String> bestMoveBySig) {
        final Map<String, Integer> lineLengthBySig = new HashMap<>();
        for (String sig : signatures) {
            lineLengthFrom(sig, adjacency, bestMoveBySig, lineLengthBySig, new HashSet<>());
        }
        return lineLengthBySig;
    }


    /**
     * Adds a directed edge once per SAN sequence to avoid duplicate expansions.
     *
     * @param adjacency       adjacency list being filled.
     * @param edgeKeysByFrom  per-signature edge keys for deduplication.
     * @param fromSig         source signature.
     * @param edge            edge descriptor to append.
     */
    private static void addEdge(
            Map<String, List<Edge>> adjacency,
            Map<String, Set<EdgeKey>> edgeKeysByFrom,
            String fromSig,
            Edge edge) {
        if (edge == null || edge.destSig == null || edge.sans == null || edge.sans.length == 0) {
            return;
        }
        if (edgeKeysByFrom != null) {
            Set<EdgeKey> seen = edgeKeysByFrom.computeIfAbsent(fromSig, k -> new HashSet<>());
            if (!seen.add(new EdgeKey(edge.destSig, edge.sans))) {
                return;
            }
        }
        adjacency.computeIfAbsent(fromSig, k -> new ArrayList<>()).add(edge);
    }

    /**
     * Builds a {@link Game} including mainline and implicit variations from the context.
     *
     * @param startSig root signature for the game.
     * @param startPos starting position.
     * @param ctx      shared context for adjacency and metadata.
     * @return constructed game, or null when no moves exist.
     */
    private static Game buildGameWithVariations(
            String startSig,
            Position startPos,
            PgnContext ctx) {
        Game game = new Game();
        game.setStartPosition(startPos != null ? startPos.copyOf() : null);
        if (ctx.descriptionBySig != null) {
            String rootComment = ctx.descriptionBySig.get(startSig);
            if (rootComment != null && !rootComment.isEmpty()) {
                game.addPreambleComment(rootComment);
            }
        }
        Set<String> pathSigs = new HashSet<>();
        if (startSig != null) {
            pathSigs.add(startSig);
        }
        Game.Node mainline = buildLine(startSig, ctx, pathSigs);
        if (mainline == null) {
            return null;
        }
        game.setMainline(mainline);
        String result = computeResult(game.getStartPosition(), mainline);
        if (result != null) {
            game.setResult(result);
        }
        return game;
    }

    /**
     * Recursively builds a line (mainline plus variations) from a signature.
     *
     * @param currentSig current position signature.
     * @param ctx        shared adjacency context.
     * @param pathSigs   signatures already visited along this path.
     * @return head node of the line.
     */
    private static Game.Node buildLine(
            String currentSig,
            PgnContext ctx,
            Set<String> pathSigs) {
        List<Edge> edges = ctx.adjacency.get(currentSig);
        if (edges == null || edges.isEmpty()) {
            return leafNode(currentSig, ctx);
        }

        List<PathOption> options = collectOptions(edges, pathSigs);
        if (options.isEmpty()) {
            return leafNode(currentSig, ctx);
        }

        List<Game.Node> nodes = buildOptions(options, ctx, pathSigs);
        if (nodes.isEmpty()) {
            return leafNode(currentSig, ctx);
        }

        Game.Node main = nodes.get(0);
        for (int i = 1; i < nodes.size(); i++) {
            main.addVariation(nodes.get(i));
        }
        return main;
    }

    /**
     * Produces a terminal node using the best move stored for a signature.
     *
     * @param sig signature of the leaf position.
     * @param ctx shared context with best-move lookup.
     * @return node for the recorded best move, or {@code null}.
     */
    private static Game.Node leafNode(String sig, PgnContext ctx) {
        String leafSan = ctx.bestMoveBySig != null ? ctx.bestMoveBySig.get(sig) : null;
        return (leafSan == null || leafSan.isEmpty()) ? null : new Game.Node(leafSan);
    }

    /**
     * Collects valid path options, excluding revisits.
     *
     * @param edges    edges from the current signature.
     * @param pathSigs visited signatures along the current variation.
     * @return list of options to explore.
     */
    private static List<PathOption> collectOptions(List<Edge> edges, Set<String> pathSigs) {
        List<PathOption> options = new ArrayList<>(edges.size());
        for (Edge edge : edges) {
            if (edge == null || edge.sans == null || edge.sans.length == 0) {
                continue;
            }
            if (edge.destSig != null && !pathSigs.contains(edge.destSig)) {
                options.add(new PathOption(edge.destSig, edge.sans, 0));
            }
        }
        return options;
    }

    /**
     * Builds nodes for the given move options, sorted and grouped by SAN.
     *
     * @param options  path options to evaluate.
     * @param ctx      shared context containing metadata maps.
     * @param pathSigs visited signatures for cycle prevention.
     * @return list of nodes representing the grouped options.
     */
    private static List<Game.Node> buildOptions(
            List<PathOption> options,
            PgnContext ctx,
            Set<String> pathSigs) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }

        List<PathOption> sorted = new ArrayList<>(options);
        sorted.sort((a, b) -> compareOptions(a, b, ctx));

        GroupResult grouping = groupOptions(sorted);
        return buildGroupedNodes(grouping, ctx, pathSigs);
    }

    /**
     * Groups sorted path options by their SAN prefix and records tail entries.
     *
     * @param sorted sorted options.
     * @return grouping result with order and buckets.
     */
    private static GroupResult groupOptions(List<PathOption> sorted) {
        List<OrderEntry> order = new ArrayList<>();
        Map<String, List<PathOption>> groups = new LinkedHashMap<>();
        for (PathOption option : sorted) {
            if (option == null) {
                continue;
            }
            if (option.isDone()) {
                order.add(OrderEntry.tail(option));
            } else {
                String san = option.currentSan();
                if (san != null && !san.isEmpty()) {
                    List<PathOption> group = groups.computeIfAbsent(san, key -> {
                        order.add(OrderEntry.san(key));
                        return new ArrayList<>();
                    });
                    group.add(option);
                }
            }
        }
        return new GroupResult(order, groups);
    }

    /**
     * Builds game nodes for each grouped entry, handling tails and SAN groups.
     *
     * @param grouping grouped options with order.
     * @param ctx      shared context.
     * @param pathSigs visited signature set.
     * @return nodes assembled from the grouping.
     */
    private static List<Game.Node> buildGroupedNodes(GroupResult grouping, PgnContext ctx, Set<String> pathSigs) {
        List<Game.Node> out = new ArrayList<>();
        for (OrderEntry entry : grouping.order()) {
            if (entry.isTail()) {
                addTailNode(entry.tail(), out, ctx, pathSigs);
            } else {
                String san = entry.san();
                List<PathOption> group = (san == null || san.isEmpty()) ? null : grouping.groups().get(san);
                if (group == null || group.isEmpty()) {
                    continue;
                }
                out.add(buildGroupNode(san, group, ctx, pathSigs));
            }
        }
        return out;
    }

    /**
     * Adds a node for tail options (options without additional SAN steps).
     *
     * @param tail     path option representing the tail.
     * @param out      list receiving constructed nodes.
     * @param ctx      shared context.
     * @param pathSigs visited signature set.
     */
    private static void addTailNode(
            PathOption tail,
            List<Game.Node> out,
            PgnContext ctx,
            Set<String> pathSigs) {
        if (tail == null || tail.destSig == null || pathSigs.contains(tail.destSig)) {
            return;
        }
        pathSigs.add(tail.destSig);
        Game.Node node = buildLine(tail.destSig, ctx, pathSigs);
        pathSigs.remove(tail.destSig);
        if (node != null) {
            out.add(node);
        }
    }

    /**
     * Builds a SAN group node, adds comments, and attaches continuations.
     *
     * @param san      move SAN.
     * @param group    options sharing the same SAN.
     * @param ctx      shared context.
     * @param pathSigs visited signatures.
     * @return node representing this SAN branch.
     */
    private static Game.Node buildGroupNode(
            String san,
            List<PathOption> group,
            PgnContext ctx,
            Set<String> pathSigs) {
        Game.Node node = new Game.Node(san);
        addGroupComment(node, group, ctx.descriptionBySig);
        List<PathOption> next = new ArrayList<>(group.size());
        for (PathOption option : group) {
            next.add(option.advance());
        }
        List<Game.Node> continuations = buildOptions(next, ctx, pathSigs);
        attachContinuations(node, continuations);
        return node;
    }

    /**
     * Adds a comment after the node when a terminal option carries a description.
     *
     * @param node             target node.
     * @param group            options sharing the same SAN.
     * @param descriptionBySig description map.
     */
    private static void addGroupComment(
            Game.Node node,
            List<PathOption> group,
            Map<String, String> descriptionBySig) {
        if (descriptionBySig == null || group == null) {
            return;
        }
        for (PathOption option : group) {
            if (option.isLast()) {
                String comment = descriptionBySig.get(option.destSig);
                if (comment != null && !comment.isEmpty()) {
                    node.addCommentAfter(comment);
                    return;
                }
            }
        }
    }

    /**
     * Attaches continuation nodes as either mainline or variations.
     *
     * @param node          parent node.
     * @param continuations continuation nodes to append.
     */
    private static void attachContinuations(Game.Node node, List<Game.Node> continuations) {
        if (continuations == null || continuations.isEmpty()) {
            return;
        }
        Game.Node mainContinuation = continuations.get(0);
        node.setNext(mainContinuation);
        if (mainContinuation != null) {
            for (int i = 1; i < continuations.size(); i++) {
                mainContinuation.addVariation(continuations.get(i));
            }
        } else {
            for (int i = 1; i < continuations.size(); i++) {
                node.addVariation(continuations.get(i));
            }
        }
    }

    /**
     * Produces the four-field FEN signature (drops move clocks).
     *
     * @param position position to canonicalize.
     * @return FEN prefix ending after the fourth token.
     */
    private static String fenSignature(Position position) {
        if (position == null) {
            return "";
        }
        String fen = position.toString();
        int spaces = 0;
        for (int i = 0; i < fen.length(); i++) {
            if (fen.charAt(i) == ' ') {
                spaces++;
                if (spaces == 4) {
                    return fen.substring(0, i);
                }
            }
        }
        return fen;
    }

    /**
     * Holds map views built from streamed records.
     */
    private static final class IndexData {

        /**
         * Map of signature to FEN.
         */
        private final Map<String, String> positionsBySig;

        /**
         * Adjacency list for move edges.
         */
        private final Map<String, List<Edge>> adjacency;

        /**
         * Set of signatures that have incoming edges.
         */
        private final Set<String> incoming;

        /**
         * Best-move SAN lookup by signature.
         */
        private final Map<String, String> bestMoveBySig;

        /**
         * Description lookup by signature.
         */
        private final Map<String, String> descriptionBySig;

        /**
         * Evaluation score lookup by signature.
         */
        private final Map<String, Integer> evalScoreBySig;

        /**
         * Creates a container for indexed position graph data.
         *
         * @param positionsBySig   map of signature to FEN string
         * @param adjacency        adjacency list of edges per signature
         * @param incoming         set of signatures with incoming edges
         * @param bestMoveBySig    map of signature to best-move SAN
         * @param descriptionBySig map of signature to descriptive text
         * @param evalScoreBySig   map of signature to evaluation score
         */
        private IndexData(
                Map<String, String> positionsBySig,
                Map<String, List<Edge>> adjacency,
                Set<String> incoming,
                Map<String, String> bestMoveBySig,
                Map<String, String> descriptionBySig,
                Map<String, Integer> evalScoreBySig) {
            this.positionsBySig = positionsBySig;
            this.adjacency = adjacency;
            this.incoming = incoming;
            this.bestMoveBySig = bestMoveBySig;
            this.descriptionBySig = descriptionBySig;
            this.evalScoreBySig = evalScoreBySig;
        }
    }

    /**
     * Lightweight child descriptor used for parent/child linking.
     */
    private static final class ChildInfo {

        /**
         * Signature of the child position.
         */
        private final String positionSig;

        /**
         * SAN for the parent-to-child move.
         */
        private final String parentToPositionSan;

        /**
         * Creates a child descriptor for parent/child linking.
         *
         * @param positionSig         child position signature
         * @param parentToPositionSan SAN for the parent-to-child move
         */
        private ChildInfo(String positionSig, String parentToPositionSan) {
            this.positionSig = positionSig;
            this.parentToPositionSan = parentToPositionSan;
        }
    }

    /**
     * Shared context passed through recursive builders.
     */
    private static final class PgnContext {

        /**
         * Adjacency list describing transitions between signatures.
         */
        private final Map<String, List<Edge>> adjacency;

        /**
         * Best-move SAN lookup for direct leaf node construction.
         */
        private final Map<String, String> bestMoveBySig;

        /**
         * Descriptions attached to specific signatures for comments.
         */
        private final Map<String, String> descriptionBySig;

        /**
         * Evaluation scores used when ordering variations.
         */
        private final Map<String, Integer> evalScoreBySig;

        /**
         * Memoized continuation lengths per signature for option sorting.
         */
        private final Map<String, Integer> lineLengthBySig;

        /**
         * @param adjacency        adjacency list with SAN sequences.
         * @param bestMoveBySig    best-move SAN map.
         * @param descriptionBySig description map keyed by signature.
         * @param evalScoreBySig   evaluation map used for sorting.
         * @param lineLengthBySig  memoized continuation lengths.
         */
        private PgnContext(
                Map<String, List<Edge>> adjacency,
                Map<String, String> bestMoveBySig,
                Map<String, String> descriptionBySig,
                Map<String, Integer> evalScoreBySig,
                Map<String, Integer> lineLengthBySig) {
            this.adjacency = adjacency;
            this.bestMoveBySig = bestMoveBySig;
            this.descriptionBySig = descriptionBySig;
            this.evalScoreBySig = evalScoreBySig;
            this.lineLengthBySig = lineLengthBySig;
        }
    }

    /**
     * Finds the SAN for a move that produces {@code child} from {@code parent}.
     *
     * @param parent source position.
     * @param child  resulting position.
     * @return SAN move when found, {@code null} otherwise.
     */
    private static String sanFromEdge(Position parent, Position child) {
        if (parent == null || child == null) {
            return null;
        }
        String target = fenSignature(child);
        MoveList moves = parent.getMoves();
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            Position next = parent.copyOf().play(move);
            if (fenSignature(next).equals(target)) {
                return SAN.toAlgebraic(parent, move);
            }
        }
        return null;
    }

    /**
     * Extracts an evaluation score for the record's primary output.
     *
     * @param rec source record.
     * @return normalized score or {@code null} when unavailable.
     */
    private static Integer evalScoreFromRecord(Record rec) {
        if (rec == null) {
            return null;
        }
        Output best = rec.getAnalysis().getBestOutput(1);
        if (best == null) {
            return null;
        }
        Evaluation eval = best.getEvaluation();
        if (eval == null || !eval.isValid()) {
            return null;
        }
        int value = eval.getValue();
        if (eval.isMate()) {
            int sign = value >= 0 ? 1 : -1;
            int mate = Math.min(9_999, Math.abs(value));
            return sign * (MATE_SCORE_BASE - mate);
        }
        return value;
    }

    /**
     * Formats the best move for a PV as SAN, falling back to UCI if SAN generation fails.
     *
     * @param pos  source position.
     * @param best best move.
     * @return SAN move or UCI fallback; empty if unavailable.
     */
    private static String formatBestMoveSan(Position pos, short best) {
        if (pos == null || best == chess.core.Move.NO_MOVE) {
            return "";
        }
        try {
            return SAN.toAlgebraic(pos, best);
        } catch (RuntimeException ex) {
            return chess.core.Move.toString(best);
        }
    }

    /**
     * Represents a directed edge with SAN strings for the path.
     */
    private static final class Edge {

        /**
         * Destination signature reached by this edge.
         */
        private final String destSig;

        /**
         * SAN sequence along this edge.
         */
        private final String[] sans;

        /**
         * @param destSig destination signature.
         * @param sans    SAN tokens describing the path.
         */
        private Edge(String destSig, String[] sans) {
            this.destSig = destSig;
            this.sans = sans;
        }
    }

    /**
     * Shared context for the bridging pass.
     */
    private static final class BridgeContext {

        /**
         * Adjacency list being populated.
         */
        private final Map<String, List<Edge>> adjacency;

        /**
         * Signatures that already have incoming edges.
         */
        private final Set<String> incoming;

        /**
         * Counter for missing SAN edges.
         */
        private final AtomicLong badEdges;

        /**
         * Direct children keyed by parent signature.
         */
        private final Map<String, Set<String>> directChildren;

        /**
         * Edge deduplication keys per source signature.
         */
        private final Map<String, Set<EdgeKey>> edgeKeysByFrom;

        /**
         * Progress bar for bridge linking.
         */
        private final Bar bridgeBar;

        /**
         * Creates a context for the bridging pass.
         *
         * @param adjacency        adjacency list being populated
         * @param incoming         set of signatures with incoming edges
         * @param badEdges         counter for missing SAN edges
         * @param directChildren   direct children keyed by parent signature
         * @param edgeKeysByFrom   edge deduplication keys per source signature
         * @param bridgeBar        progress bar for bridge linking (may be null)
         */
        private BridgeContext(
                Map<String, List<Edge>> adjacency,
                Set<String> incoming,
                AtomicLong badEdges,
                Map<String, Set<String>> directChildren,
                Map<String, Set<EdgeKey>> edgeKeysByFrom,
                Bar bridgeBar) {
            this.adjacency = adjacency;
            this.incoming = incoming;
            this.badEdges = badEdges;
            this.directChildren = directChildren;
            this.edgeKeysByFrom = edgeKeysByFrom;
            this.bridgeBar = bridgeBar;
        }
    }

    /**
     * Uniquely identifies an edge per source signature using destination and SAN path.
     */
    private static final class EdgeKey {

        /**
         * Destination signature.
         */
        private final String destSig;

        /**
         * SAN path tokens for the edge.
         */
        private final String[] sans;

        /**
         * Precomputed hash code for fast lookup.
         */
        private final int hash;

        /**
         * Creates a deduplication key for an edge.
         *
         * @param destSig destination signature
         * @param sans    SAN path tokens for the edge
         */
        private EdgeKey(String destSig, String[] sans) {
            this.destSig = destSig;
            this.sans = sans;
            this.hash = computeHash(destSig, sans);
        }

        /**
         * Computes a combined hash for the destination signature and SAN path.
         * Uses a stable 31x rolling hash over the SAN tokens.
         *
         * @param destSig destination signature
         * @param sans SAN path tokens for the edge
         * @return combined hash code
         */
        private static int computeHash(String destSig, String[] sans) {
            int result = (destSig != null) ? destSig.hashCode() : 0;
            if (sans != null) {
                for (String san : sans) {
                    result = 31 * result + (san != null ? san.hashCode() : 0);
                }
            }
            return result;
        }

        /**
         * Compares destination signature and SAN path content for equality.
         * Treats SAN tokens as ordered path elements.
         *
         * @param obj object to compare against
         * @return true if both keys represent the same edge
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof EdgeKey other)) {
                return false;
            }
            return Objects.equals(destSig, other.destSig) && Arrays.equals(sans, other.sans);
        }

        /**
         * Returns the precomputed hash for this key.
         * Matches {@link #equals(Object)} semantics.
         *
         * @return hash code for this key
         */
        @Override
        public int hashCode() {
            return hash;
        }
    }

    /**
     * Tracks progression through an edge's SAN sequence.
     */
    private static final class PathOption {

        /**
         * Destination signature this option aims for.
         */
        private final String destSig;

        /**
         * SAN strings that describe path steps.
         */
        private final String[] sans;

        /**
         * Current index inside {@link #sans}.
         */
        private final int index;

        /**
         * @param destSig destination signature.
         * @param sans    remaining SAN tokens.
         * @param index   position inside {@code sans}.
         */
        private PathOption(String destSig, String[] sans, int index) {
            this.destSig = destSig;
            this.sans = sans;
            this.index = index;
        }

        /**
         * Indicates whether this option has been fully consumed.
         */
        private boolean isDone() {
            return sans == null || index >= sans.length;
        }

        /**
         * Checks if this index refers to the last SAN token.
         */
        private boolean isLast() {
            return sans != null && index == sans.length - 1;
        }

        /**
         * Returns the SAN at the current index.
         */
        private String currentSan() {
            return (sans == null || index >= sans.length) ? "" : sans[index];
        }

        /**
         * Advances to the next SAN token and returns a new {@code PathOption}.
         */
        private PathOption advance() {
            return new PathOption(destSig, sans, index + 1);
        }

        /**
         * @return number of SAN tokens remaining.
         */
        private int remainingPlies() {
            return (sans == null) ? 0 : Math.max(0, sans.length - index);
        }
    }

    /**
     * Captures the ordering of grouped options plus their buckets.
     *
     * @param order  ordered entries (tails and SAN groups).
     * @param groups map from SAN to options sharing that SAN.
     */
    private record GroupResult(List<OrderEntry> order, Map<String, List<PathOption>> groups) {
    }

    /**
     * Represents either a SAN group entry or an end-of-path tail.
     *
     * @param san  SAN for the grouped entry (null for tails).
     * @param tail tail option when no further SAN strings remain.
     */
    private record OrderEntry(String san, PathOption tail) {

        /**
         * Creates an entry representing a SAN group.
         *
         * @param san SAN value to track.
         * @return group entry.
         */
        private static OrderEntry san(String san) {
            return new OrderEntry(san, null);
        }

        /**
         * Creates an entry representing a tail continuation.
         *
         * @param tail option ending the path.
         * @return tail entry.
         */
        private static OrderEntry tail(PathOption tail) {
            return new OrderEntry(null, tail);
        }

        /**
         * @return {@code true} when this entry represents a tail.
         */
        private boolean isTail() {
            return tail != null;
        }
    }

    /**
     * Determines the PGN result by walking the mainline until a mate is reached.
     *
     * @param startPos starting position.
     * @param mainline mainline nodes.
     * @return PGN result token or {@code null} if unresolved.
     */
    private static String computeResult(Position startPos, Game.Node mainline) {
        if (mainline == null || startPos == null) {
            return null;
        }
        int plies = 0;
        Game.Node cur = mainline;
        String lastSan = null;
        while (cur != null) {
            plies++;
            lastSan = cur.getSan();
            cur = cur.getNext();
        }
        if (lastSan == null || !lastSan.endsWith("#")) {
            return null;
        }
        boolean startBlack = startPos.isBlackTurn();
        boolean lastByWhite = startBlack ? (plies % 2 == 0) : (plies % 2 == 1);
        return lastByWhite ? SAN.RESULT_WHITE_WIN : SAN.RESULT_BLACK_WIN;
    }

    /**
     * Orders options first by evaluation score then by remaining line length.
     *
     * @param a   first path option.
     * @param b   second path option.
     * @param ctx shared context with evaluation and length info.
     * @return negative when {@code a} should run before {@code b}.
     */
    private static int compareOptions(
            PathOption a,
            PathOption b,
            PgnContext ctx) {
        int scoreA = optionScore(a, ctx.evalScoreBySig);
        int scoreB = optionScore(b, ctx.evalScoreBySig);
        if (scoreA != scoreB) {
            return Integer.compare(scoreB, scoreA);
        }
        int lenA = optionLength(a, ctx.lineLengthBySig);
        int lenB = optionLength(b, ctx.lineLengthBySig);
        return Integer.compare(lenB, lenA);
    }

    /**
     * Normalizes the evaluation score for comparison, accounting for move parity.
     *
     * @param option         path option to score.
     * @param evalScoreBySig map of scores keyed by signature.
     * @return adjusted score for sorting.
     */
    private static int optionScore(PathOption option, Map<String, Integer> evalScoreBySig) {
        if (option == null || evalScoreBySig == null || option.destSig == null) {
            return Integer.MIN_VALUE / 2;
        }
        Integer raw = evalScoreBySig.get(option.destSig);
        int score = (raw == null) ? Integer.MIN_VALUE / 2 : raw;
        int remainingPlies = option.remainingPlies();
        if ((remainingPlies & 1) == 1) {
            score = -score;
        }
        return score;
    }

    /**
     * Computes the remaining length of a path option, including memoized continuations.
     *
     * @param option          path option to length.
     * @param lineLengthBySig memoized lengths for signatures.
     * @return total remaining plies.
     */
    private static int optionLength(PathOption option, Map<String, Integer> lineLengthBySig) {
        if (option == null) {
            return 0;
        }
        int remaining = option.remainingPlies();
        if (lineLengthBySig == null || option.destSig == null) {
            return remaining;
        }
        return remaining + lineLengthBySig.getOrDefault(option.destSig, 0);
    }

    /**
     * Recursively computes the length of the best continuation from a signature.
     *
     * @param sig           current signature.
     * @param adjacency     adjacency information.
     * @param bestMoveBySig recorded best moves.
     * @param memo          memoization map.
     * @param visiting      set of signatures currently being visited to avoid cycles.
     * @return length of the best continuation line.
     */
    private static int lineLengthFrom(
            String sig,
            Map<String, List<Edge>> adjacency,
            Map<String, String> bestMoveBySig,
            Map<String, Integer> memo,
            Set<String> visiting) {
        if (sig == null) {
            return 0;
        }
        Integer cached = memo.get(sig);
        if (cached != null) {
            return cached;
        }
        if (visiting.contains(sig)) {
            return 0;
        }
        visiting.add(sig);
        int best = 0;
        if (bestMoveBySig != null && bestMoveBySig.get(sig) != null) {
            best = 1;
        }
        List<Edge> edges = adjacency.get(sig);
        if (edges != null) {
            for (Edge edge : edges) {
                if (edge == null || edge.destSig == null || edge.sans == null) {
                    continue;
                }
                int len = edge.sans.length + lineLengthFrom(edge.destSig, adjacency, bestMoveBySig, memo, visiting);
                if (len > best) {
                    best = len;
                }
            }
        }
        visiting.remove(sig);
        memo.put(sig, best);
        return best;
    }

    /**
     * Streams JSON objects from either a top-level array or JSONL file.
     *
     * @param recordFile input JSON path
     * @param consumer   receiver for each JSON object
     * @throws IOException when reading fails
     */
    private static void streamRecordObjects(Path recordFile, Consumer<String> consumer) throws IOException {
        if (isJsonArrayFile(recordFile)) {
            Json.streamTopLevelObjects(recordFile, consumer);
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(recordFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                consumer.accept(trimmed);
            }
        }
    }

    /**
     * Detects whether the input begins with a JSON array.
     *
     * @param input input path
     * @return {@code true} when the file looks like a JSON array
     * @throws IOException when the file cannot be read
     */
    private static boolean isJsonArrayFile(Path input) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
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
     * Counts top-level records for progress reporting; returns {@code 0} on failure.
     *
     * @param recordFile input JSON array or JSONL file.
     * @return total record count, or {@code 0} when counting fails.
     */
    private static long countRecords(Path recordFile) {
        if (recordFile == null) {
            return 0L;
        }
        final AtomicLong count = new AtomicLong();
        try {
            streamRecordObjects(recordFile, objJson -> count.incrementAndGet());
        } catch (IOException ex) {
            LogService.warn(String.format(
                    "Unable to count records in '%s'; progress bar disabled.",
                    recordFile));
            return 0L;
        }
        return count.get();
    }

    /**
     * Builds a progress bar capped to int range; returns null when total is unknown.
     *
     * @param totalRecords total record count (may be 0 or negative for unknown).
     * @param label        label prefix for the bar.
     * @return progress bar instance or {@code null} if disabled.
     */
    private static Bar progressBar(long totalRecords, String label) {
        if (totalRecords <= 0) {
            return null;
        }
        int capped = (totalRecords > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) totalRecords;
        return new Bar(capped, label);
    }

}
