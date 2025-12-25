package chess.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

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
     * Converts a {@code .record} JSON array file into PGN games by linking records
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
     * @param recordFile input JSON (array) path.
     * @param pgnFile    output PGN path (must not be null).
     * @throws IllegalArgumentException if {@code recordFile} or {@code pgnFile} is null.
     */
    public static void export(Path recordFile, Path pgnFile) {
        if (recordFile == null) {
            throw new IllegalArgumentException("recordfile is null");
        }
        if (pgnFile == null) {
            throw new IllegalArgumentException("pgnFile is null");
        }

        final AtomicLong ok = new AtomicLong();
        final AtomicLong bad = new AtomicLong();
        final AtomicLong badEdges = new AtomicLong();

        LogService.info(String.format(
                "Converting records to PGN '%s' to '%s'.",
                recordFile, pgnFile));

        try {
            List<Record> records = readRecords(recordFile, ok, bad);
            if (records.isEmpty()) {
                LogService.info(String.format(
                        "No records parsed from '%s'; skipping PGN output '%s'.",
                        recordFile, pgnFile));
                return;
            }

            PgnIndex index = indexRecords(records);
            GraphData graph = buildGraph(index, badEdges);
            List<String> startSigs = computeStartSignatures(index.positionsBySig, graph.incoming);
            if (startSigs.isEmpty()) {
                LogService.info(String.format(
                        "No root positions found in '%s'; skipping PGN output '%s'.",
                        recordFile, pgnFile));
                return;
            }

            Map<String, Integer> lineLengthBySig = computeLineLengths(index.positionsBySig.keySet(), graph.adjacency,
                    index.bestMoveBySig);
            PgnContext ctx = new PgnContext(
                    graph.adjacency,
                    index.bestMoveBySig,
                    index.descriptionBySig,
                    index.evalScoreBySig,
                    lineLengthBySig);
            List<Game> games = buildGames(startSigs, index.positionsBySig, ctx);
            if (games.isEmpty()) {
                LogService.info(String.format(
                        "No PGN games produced from '%s'; skipping output '%s'.",
                        recordFile, pgnFile));
                return;
            }

            if (!writePgnOutput(pgnFile, games, recordFile)) {
                return;
            }

            LogService.info(String.format(
                    "Completed record to PGN conversion '%s' to '%s' and wrote %d games (bad records=%d, bad edges=%d).",
                    recordFile, pgnFile, games.size(), bad.get(), badEdges.get()));
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
     * @param pgnFile    destination PGN path.
     * @param games      parsed games to serialize.
     * @param recordFile source record file for log context.
     * @return {@code true} if the write succeeded.
     */
    private static boolean writePgnOutput(Path pgnFile, List<Game> games, Path recordFile) {
        try {
            Writer.writePgn(pgnFile, games);
            return true;
        } catch (IOException e) {
            LogService.error(
                    e,
                    String.format(
                            "Failed to write PGN output '%s' from '%s'.",
                            pgnFile, recordFile));
            return false;
        }
    }

    /**
     * Streams records from the JSON file, collecting valid entries and tracking counts.
     *
     * @param recordFile source JSON path.
     * @param ok         counter for valid records.
     * @param bad        counter for skipped records.
     * @return list of parsed records that contain a position.
     * @throws IOException if the stream fails.
     */
    private static List<Record> readRecords(Path recordFile, AtomicLong ok, AtomicLong bad)
            throws IOException {
        final List<Record> records = new ArrayList<>();
        Json.streamTopLevelObjects(recordFile, objJson -> {
            Record rec = Record.fromJson(objJson);
            if (rec == null || rec.getPosition() == null) {
                bad.incrementAndGet();
                return;
            }
            records.add(rec);
            ok.incrementAndGet();
        });
        return records;
    }

    /**
     * Indexes parsed records into nodes and lookup maps used for graph construction.
     *
     * @param records parsed records to index.
     * @return {@link PgnIndex} holding nodes plus map views.
     */
    private static PgnIndex indexRecords(List<Record> records) {
        final List<RecordNode> nodes = new ArrayList<>(records.size());
        final Map<String, Position> positionsBySig = new LinkedHashMap<>();
        final Map<String, List<RecordNode>> childrenByParentSig = new HashMap<>();
        final Map<String, String> bestMoveBySig = new HashMap<>();
        final Map<String, String> descriptionBySig = new HashMap<>();
        final Map<String, Integer> evalScoreBySig = new HashMap<>();

        for (Record rec : records) {
            if (rec.getPosition() == null) {
                continue;
            }
            RecordNode node = new RecordNode(rec);
            nodes.add(node);
            positionsBySig.putIfAbsent(node.positionSig, node.position);
            if (node.bestMoveSan != null && !node.bestMoveSan.isEmpty()) {
                bestMoveBySig.putIfAbsent(node.positionSig, node.bestMoveSan);
            }
            if (node.description != null && !node.description.isEmpty()) {
                descriptionBySig.putIfAbsent(node.positionSig, node.description);
            }
            if (node.evalScore != null) {
                evalScoreBySig.putIfAbsent(node.positionSig, node.evalScore);
            }
            if (node.parentSig != null) {
                childrenByParentSig.computeIfAbsent(node.parentSig, k -> new ArrayList<>()).add(node);
            }
        }

        return new PgnIndex(nodes, positionsBySig, childrenByParentSig, bestMoveBySig, descriptionBySig, evalScoreBySig);
    }

    /**
     * Builds adjacency relationships between positions, tagging incoming nodes.
     *
     * @param index    indexed record data.
     * @param badEdges counter for edges that could not be formed.
     * @return adjacency graph plus incoming signature set.
     */
    private static GraphData buildGraph(PgnIndex index, AtomicLong badEdges) {
        final Map<String, List<Edge>> adjacency = new LinkedHashMap<>();
        final Set<String> incoming = new HashSet<>();
        addDirectEdges(index.nodes, index.positionsBySig, adjacency, incoming, badEdges);
        addBridgedEdges(index.nodes, index.childrenByParentSig, adjacency, incoming, badEdges);
        return new GraphData(adjacency, incoming);
    }

    /**
     * Adds edges where the parent position is explicitly stored as another record.
     *
     * @param nodes          record nodes to inspect.
     * @param positionsBySig positions available in the dataset.
     * @param adjacency      adjacency list under construction.
     * @param incoming       set of signatures with incoming edges.
     * @param badEdges       counter incremented when edge data is missing.
     */
    private static void addDirectEdges(
            List<RecordNode> nodes,
            Map<String, Position> positionsBySig,
            Map<String, List<Edge>> adjacency,
            Set<String> incoming,
            AtomicLong badEdges) {
        for (RecordNode node : nodes) {
            if (node.parentSig == null || !positionsBySig.containsKey(node.parentSig)) {
                continue;
            }
            if (node.parentToPositionSan == null) {
                badEdges.incrementAndGet();
            } else {
                addEdge(adjacency, node.parentSig,
                        new Edge(node.positionSig, new String[] { node.parentToPositionSan }));
                incoming.add(node.positionSig);
            }
        }
    }

    /**
     * Generates synthetic connections by exploring legal moves from recorded nodes.
     *
     * @param nodes               record nodes to expand.
     * @param childrenByParentSig children grouped by parent signatures.
     * @param adjacency           adjacency list being filled.
     * @param incoming            set of signatures with incoming edges.
     * @param badEdges            counter for invalid child references.
     */
    private static void addBridgedEdges(
            List<RecordNode> nodes,
            Map<String, List<RecordNode>> childrenByParentSig,
            Map<String, List<Edge>> adjacency,
            Set<String> incoming,
            AtomicLong badEdges) {
        for (RecordNode node : nodes) {
            addBridgedEdgesForNode(node, childrenByParentSig, adjacency, incoming, badEdges);
        }
    }

    /**
     * Handles bridging logic for a single node.
     *
     * @param node               node whose moves are explored.
     * @param childrenByParentSig child mapping by parent signature.
     * @param adjacency           adjacency list being filled.
     * @param incoming            seen incoming signatures.
     * @param badEdges            counter for missing SAN strings.
     */
    private static void addBridgedEdgesForNode(
            RecordNode node,
            Map<String, List<RecordNode>> childrenByParentSig,
            Map<String, List<Edge>> adjacency,
            Set<String> incoming,
            AtomicLong badEdges) {
        Position pos = node.position;
        MoveList moves = pos != null ? pos.getMoves() : null;
        if (moves == null || moves.size() == 0) {
            return;
        }
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            addBridgedEdgesForMove(node.positionSig, pos, move, childrenByParentSig, adjacency, incoming, badEdges);
        }
    }

    /**
     * Processes one legal move from a position and links to child nodes.
     *
     * @param fromSig             signature of the starting position.
     * @param pos                 current position.
     * @param move                move being considered.
     * @param childrenByParentSig child mapping keyed by signatures.
     * @param adjacency           adjacency list being filled.
     * @param incoming            set of incoming sigs.
     * @param badEdges            counter for missing SAN data.
     */
    private static void addBridgedEdgesForMove(
            String fromSig,
            Position pos,
            short move,
            Map<String, List<RecordNode>> childrenByParentSig,
            Map<String, List<Edge>> adjacency,
            Set<String> incoming,
            AtomicLong badEdges) {
        Position next = pos.copyOf().play(move);
        List<RecordNode> kids = childrenByParentSig.get(fenSignature(next));
        if (kids == null || kids.isEmpty()) {
            return;
        }
        String san = SAN.toAlgebraic(pos, move);
        addBridgedEdgesForKids(fromSig, san, kids, adjacency, incoming, badEdges);
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
            List<RecordNode> kids,
            Map<String, List<Edge>> adjacency,
            Set<String> incoming,
            AtomicLong badEdges) {
        for (RecordNode kid : kids) {
            if (kid.parentToPositionSan == null) {
                badEdges.incrementAndGet();
                continue;
            }
            addEdge(adjacency, fromSig,
                    new Edge(kid.positionSig, new String[] { san, kid.parentToPositionSan }));
            incoming.add(kid.positionSig);
        }
    }

    /**
     * Determines which signatures lack incoming edges and therefore form roots.
     *
     * @param positionsBySig map of available signatures.
     * @param incoming       signatures that have incoming links.
     * @return candidate root signatures; falls back to all signatures when none are isolated.
     */
    private static List<String> computeStartSignatures(
            Map<String, Position> positionsBySig,
            Set<String> incoming) {
        final List<String> startSigs = new ArrayList<>();
        for (String posSig : positionsBySig.keySet()) {
            if (!incoming.contains(posSig)) {
                startSigs.add(posSig);
            }
        }
        if (startSigs.isEmpty()) {
            startSigs.addAll(positionsBySig.keySet());
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
     * Builds PGN game structures starting from each root signature.
     *
     * @param startSigs      candidate root signatures.
     * @param positionsBySig map of starting positions by signature.
     * @param ctx            shared context with adjacency and metadata.
     * @return list of games produced from the graph.
     */
    private static List<Game> buildGames(
            List<String> startSigs,
            Map<String, Position> positionsBySig,
            PgnContext ctx) {
        final List<Game> games = new ArrayList<>();
        for (String startSig : startSigs) {
            Position startPos = positionsBySig.get(startSig);
            if (startPos == null) {
                continue;
            }
            Game game = buildGameWithVariations(startSig, startPos, ctx);
            if (game != null) {
                games.add(game);
            }
        }
        return games;
    }

    /**
     * Adds a directed edge from {@code fromSig} when SAN data exists.
     *
     * @param adjacency adjacency list being filled.
     * @param fromSig   source signature.
     * @param edge      edge descriptor to append.
     */
    private static void addEdge(Map<String, List<Edge>> adjacency, String fromSig, Edge edge) {
        if (edge == null || edge.sans == null || edge.sans.length == 0) {
            return;
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
     * Holds nodes and supporting maps derived from the input records.
     */
    private static final class PgnIndex {
        /**
         * Parsed record nodes ordered as read; available for adjacency construction.
         */
        private final List<RecordNode> nodes;

        /**
         * Positions keyed by their FEN signatures for direct lookup when building edges.
         */
        private final Map<String, Position> positionsBySig;

        /**
         * Child nodes grouped by the signature of their recorded parent position.
         */
        private final Map<String, List<RecordNode>> childrenByParentSig;

        /**
         * SAN strings describing the best move stored for each signature.
         */
        private final Map<String, String> bestMoveBySig;

        /**
         * Optional description text that accompanies a signature when present.
         */
        private final Map<String, String> descriptionBySig;

        /**
         * Normalized evaluation scores keyed by signature for ordering PVs.
         */
        private final Map<String, Integer> evalScoreBySig;

        /**
         * @param nodes               parsed nodes preserved in input order for reconstruction.
         * @param positionsBySig      lookup of unique positions by their FEN signatures.
         * @param childrenByParentSig map from parent signatures to child nodes for bridging.
         * @param bestMoveBySig       recorded best-move SAN per signature for leaf hints.
         * @param descriptionBySig    optional description text attached to each signature.
         * @param evalScoreBySig      evaluation map used when sorting continuations.
         */
        private PgnIndex(
                List<RecordNode> nodes,
                Map<String, Position> positionsBySig,
                Map<String, List<RecordNode>> childrenByParentSig,
                Map<String, String> bestMoveBySig,
                Map<String, String> descriptionBySig,
                Map<String, Integer> evalScoreBySig) {
            this.nodes = nodes;
            this.positionsBySig = positionsBySig;
            this.childrenByParentSig = childrenByParentSig;
            this.bestMoveBySig = bestMoveBySig;
            this.descriptionBySig = descriptionBySig;
            this.evalScoreBySig = evalScoreBySig;
        }
    }

    /**
     * Holds the adjacency list and incoming signature set for the PGN graph.
     */
    private static final class GraphData {
        /**
         * Adjacency list keyed by signature for the graph.
         */
        private final Map<String, List<Edge>> adjacency;

        /**
         * Signatures that have at least one incoming edge.
         */
        private final Set<String> incoming;

        /**
         * @param adjacency adjacency list built from records.
         * @param incoming  set of signatures with incoming edges.
         */
        private GraphData(Map<String, List<Edge>> adjacency, Set<String> incoming) {
            this.adjacency = adjacency;
            this.incoming = incoming;
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
     * Wraps a {@link Record} with derived metadata for graph construction.
     */
    private static final class RecordNode {
        /**
         * Recorded position used for branching.
         */
        private final Position position;

        /**
         * Parent position, when present.
         */
        private final Position parent;

        /**
         * Signature derived from {@link #position}.
         */
        private final String positionSig;

        /**
         * Signature derived from {@link #parent}.
         */
        private final String parentSig;

        /**
         * SAN describing the edge from parent to this position.
         */
        private final String parentToPositionSan;

        /**
         * Best-move SAN determined for this node.
         */
        private final String bestMoveSan;

        /**
         * Cached evaluation score for ordering variations.
         */
        private final Integer evalScore;

        /**
         * Trimmed description text for PGN comments, if provided.
         */
        private final String description;

        /**
         * @param rec source record.
         */
        private RecordNode(Record rec) {
            this.position = rec.getPosition();
            this.parent = rec.getParent();
            this.positionSig = fenSignature(this.position);
            this.parentSig = this.parent != null ? fenSignature(this.parent) : null;
            this.parentToPositionSan = this.parent != null ? sanFromEdge(this.parent, this.position) : null;
            this.bestMoveSan = formatBestMoveSan(this.position, rec.getAnalysis().getBestMove());
            this.evalScore = evalScoreFromRecord(rec);
            String desc = rec.getDescription();
            if (desc != null) {
                desc = desc.trim();
            }
            this.description = (desc == null || desc.isEmpty()) ? null : desc;
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

}
