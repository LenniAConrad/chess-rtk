/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.game;

import application.gui.workbench.game.PuzzleNode.MoveActor;
import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.struct.Game;
import chess.struct.Pgn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Native Java puzzle-session engine mirroring the chess-web puzzle flow.
 */
public final class PuzzleSession {

    /**
     * Branch traversal strategy.
     */
    public enum VariationMode {
        /**
         * Explore opponent-response variation branches after the main line.
         */
        EXPLORE,

        /**
         * Follow only the PGN main line.
         */
        MAINLINE
    }

    /**
     * Result of a solver move.
     */
    public enum StepResult {
        /**
         * The move matched the current puzzle continuation.
         */
        CORRECT,

        /**
         * The move was legal but not the expected solution move.
         */
        INCORRECT,

        /**
         * The puzzle has no remaining branches to solve.
         */
        COMPLETED
    }

    /**
     * Cursor into one built puzzle line.
     *
     * @param lineIndex selected line index
     * @param cursorIndex index inside the selected line
     */
    public record Cursor(int lineIndex, int cursorIndex) { }

    /**
     * UI-ready snapshot of the current puzzle state.
     *
     * @param nodeId active node identifier
     * @param fen active FEN
     * @param whiteToMove true when white is to move
     * @param variationMode active traversal mode
     * @param lineIndex selected line index
     * @param totalLines total branch count
     * @param completedBranches branch count completed before the active branch
     * @param solved true when all branches are complete
     */
    public record Snapshot(
            int nodeId,
            String fen,
            boolean whiteToMove,
            VariationMode variationMode,
            int lineIndex,
            int totalLines,
            int completedBranches,
            boolean solved) { }

    /**
     * Move execution response.
     *
     * @param cursor resulting session cursor
     * @param solved true when all branches are complete
     * @param result step result
     * @param expectedMove expected move for this step, or {@link Move#NO_MOVE}
     * @param autoPlayedMoves opponent moves auto-played while advancing
     * @param rewindFens branch-rewind FENs crossed during variation switching
     * @param skippedSimilarVariations number of branches skipped as duplicates
     * @param snapshot resulting snapshot
     */
    public record MoveResponse(
            Cursor cursor,
            boolean solved,
            StepResult result,
            short expectedMove,
            List<Short> autoPlayedMoves,
            List<String> rewindFens,
            int skippedSimilarVariations,
            Snapshot snapshot) { }

    /**
     * Hint response for the current user decision.
     *
     * @param move best expected move, or {@link Move#NO_MOVE}
     * @param fromSquare source square, or -1 when no hint exists
     * @param snapshot current snapshot
     * @param solved true when the puzzle is already complete
     */
    public record Hint(short move, byte fromSquare, Snapshot snapshot, boolean solved) { }

    /**
     * Synthetic root node identifier.
     */
    private static final int ROOT_ID = 1;

    /**
     * Default event title when PGN tags do not name the puzzle.
     */
    private static final String UNTITLED = "Untitled Puzzle";

    /**
     * Puzzle title shown in the UI.
     */
    private final String title;

    /**
     * Source label for diagnostics.
     */
    private final String source;

    /**
     * Root FEN.
     */
    private final String startFen;

    /**
     * Normalized puzzle nodes in insertion order.
     */
    private final List<PuzzleNode> nodes;

    /**
     * Node lookup by identifier.
     */
    private final Map<Integer, PuzzleNode> nodeById = new LinkedHashMap<>();

    /**
     * Children grouped by parent identifier.
     */
    private final Map<Integer, List<PuzzleNode>> childrenByParent = new HashMap<>();

    /**
     * Built branch lines, each as node identifiers from root to leaf.
     */
    private final List<List<Integer>> lines;

    /**
     * Active traversal strategy.
     */
    private final VariationMode mode;

    /**
     * True when the solver plays white from the root FEN.
     */
    private final boolean userWhite;

    /**
     * Active cursor.
     */
    private Cursor cursor = new Cursor(0, 0);

    /**
     * Whether all branches have been solved.
     */
    private boolean solved;

    /**
     * Number of wrong moves entered.
     */
    private int wrongMoveCount;

    /**
     * Number of hints requested.
     */
    private int hintCount;

    /**
     * Number of solution reveals requested.
     */
    private int revealCount;

    /**
     * Number of similar branches skipped by the engine.
     */
    private int skippedSimilarVariationCount;

    /**
     * Creates a session from normalized puzzle nodes.
     *
     * @param title puzzle title
     * @param source source label
     * @param startFen root FEN
     * @param nodes normalized nodes
     * @param mode branch traversal strategy
     */
    private PuzzleSession(String title, String source, String startFen, List<PuzzleNode> nodes, VariationMode mode) {
        this.title = title == null || title.isBlank() ? UNTITLED : title.trim();
        this.source = source == null || source.isBlank() ? "pgn" : source.trim();
        this.startFen = startFen;
        this.nodes = List.copyOf(nodes);
        this.mode = mode == null ? VariationMode.EXPLORE : mode;
        for (PuzzleNode node : nodes) {
            nodeById.put(Integer.valueOf(node.id()), node);
            if (!node.root()) {
                childrenByParent.computeIfAbsent(Integer.valueOf(node.parentId()), key -> new ArrayList<>()).add(node);
            }
        }
        for (List<PuzzleNode> children : childrenByParent.values()) {
            children.sort((a, b) -> {
                int byOrder = Integer.compare(a.siblingOrder(), b.siblingOrder());
                return byOrder != 0 ? byOrder : Integer.compare(a.id(), b.id());
            });
        }
        PuzzleNode root = nodeById.get(Integer.valueOf(ROOT_ID));
        if (root == null) {
            throw new IllegalArgumentException("Puzzle root not found");
        }
        userWhite = whiteToMove(root.fenAfter());
        lines = buildLines();
        if (lines.isEmpty()) {
            lines.add(List.of(Integer.valueOf(ROOT_ID)));
        }
    }

    /**
     * Parses a PGN puzzle into a session.
     *
     * @param pgnText PGN text with optional variations
     * @param source source label
     * @param mode branch traversal strategy
     * @return parsed puzzle session
     */
    public static PuzzleSession fromPgn(String pgnText, String source, VariationMode mode) {
        List<Game> games = Pgn.parseGames(pgnText == null ? "" : pgnText);
        if (games.isEmpty()) {
            throw new IllegalArgumentException("PGN did not contain a puzzle game");
        }
        return fromGame(games.get(0), source, mode);
    }

    /**
     * Builds a session from an already parsed PGN game.
     *
     * @param game parsed PGN game
     * @param source source label
     * @param mode branch traversal strategy
     * @return puzzle session
     */
    public static PuzzleSession fromGame(Game game, String source, VariationMode mode) {
        if (game == null || (game.getMainline() == null && game.getRootVariations().isEmpty())) {
            throw new IllegalArgumentException("Puzzle game has no moves");
        }
        String title = game.getTags().getOrDefault("Event", UNTITLED);
        Position rootPosition = game.getStartPosition();
        String rootFen = rootPosition.toString();
        TreeBuilder builder = new TreeBuilder(rootPosition.isWhiteToMove(), rootFen);
        for (Game.Node variation : game.getRootVariations()) {
            builder.processLine(variation, ROOT_ID, 0, rootPosition, false);
        }
        builder.processLine(game.getMainline(), ROOT_ID, 0, rootPosition, true);
        return new PuzzleSession(title, source, rootFen, builder.nodes(), mode);
    }

    /**
     * Resets the session to its initial cursor.
     */
    public void reset() {
        cursor = new Cursor(0, 0);
        solved = false;
        wrongMoveCount = 0;
        hintCount = 0;
        revealCount = 0;
        skippedSimilarVariationCount = 0;
    }

    /**
     * Plays one user move.
     *
     * @param move CRTK move encoding
     * @param skipSimilarVariations true to skip duplicate branch continuations
     * @return move response
     */
    public MoveResponse playUserMove(short move, boolean skipSimilarVariations) {
        SyncResult before = sync(cursor, skipSimilarVariations);
        applySync(before);
        if (solved) {
            return completedResponse(Move.NO_MOVE, before);
        }
        PuzzleNode expected = nextUserMoveNode(cursor);
        if (expected == null) {
            solved = true;
            return completedResponse(Move.NO_MOVE, before);
        }
        if (expected.move() != move) {
            wrongMoveCount++;
            return new MoveResponse(cursor, false, StepResult.INCORRECT, expected.move(),
                    before.autoPlayedMoves(), before.rewindFens(), before.skippedSimilarVariations(), snapshot());
        }
        cursor = new Cursor(cursor.lineIndex(), cursor.cursorIndex() + 1);
        SyncResult after = sync(cursor, skipSimilarVariations);
        List<Short> autoPlayed = concatMoves(before.autoPlayedMoves(), after.autoPlayedMoves());
        List<String> rewindFens = concatStrings(before.rewindFens(), after.rewindFens());
        applySync(after);
        return new MoveResponse(cursor, solved, solved ? StepResult.COMPLETED : StepResult.CORRECT,
                expected.move(), autoPlayed, rewindFens,
                before.skippedSimilarVariations() + after.skippedSimilarVariations(), snapshot());
    }

    /**
     * Reveals and plays the expected move.
     *
     * @param skipSimilarVariations true to skip duplicate branch continuations
     * @return move response
     */
    public MoveResponse reveal(boolean skipSimilarVariations) {
        SyncResult before = sync(cursor, skipSimilarVariations);
        applySync(before);
        if (solved) {
            return completedResponse(Move.NO_MOVE, before);
        }
        PuzzleNode expected = nextUserMoveNode(cursor);
        if (expected == null) {
            solved = true;
            return completedResponse(Move.NO_MOVE, before);
        }
        revealCount++;
        cursor = new Cursor(cursor.lineIndex(), cursor.cursorIndex() + 1);
        SyncResult after = sync(cursor, skipSimilarVariations);
        List<Short> autoPlayed = concatMoves(before.autoPlayedMoves(), after.autoPlayedMoves());
        List<String> rewindFens = concatStrings(before.rewindFens(), after.rewindFens());
        applySync(after);
        return new MoveResponse(cursor, solved, solved ? StepResult.COMPLETED : StepResult.CORRECT,
                expected.move(), autoPlayed, rewindFens,
                before.skippedSimilarVariations() + after.skippedSimilarVariations(), snapshot());
    }

    /**
     * Skips the active variation branch when another branch exists.
     *
     * @param skipSimilarVariations true to skip duplicate branch continuations
     * @return move response after the branch transition
     */
    public MoveResponse skipVariation(boolean skipSimilarVariations) {
        Cursor normalized = normalize(cursor);
        if (mode == VariationMode.MAINLINE || lines.size() <= 1) {
            SyncResult synced = sync(normalized, skipSimilarVariations);
            applySync(synced);
            return new MoveResponse(cursor, solved, solved ? StepResult.COMPLETED : StepResult.CORRECT,
                    Move.NO_MOVE, synced.autoPlayedMoves(), synced.rewindFens(),
                    synced.skippedSimilarVariations(), snapshot());
        }
        Transition transition = nextLineTransition(normalized.lineIndex(), normalized.cursorIndex(),
                skipSimilarVariations);
        if (transition.nextLineIndex() < 0) {
            skippedSimilarVariationCount += transition.skippedSimilarVariations();
            solved = true;
            return new MoveResponse(cursor, true, StepResult.COMPLETED, Move.NO_MOVE,
                    List.of(), transition.rewindFens(), transition.skippedSimilarVariations(), snapshot());
        }
        cursor = new Cursor(transition.nextLineIndex(), transition.targetCursorIndex());
        SyncResult synced = sync(cursor, skipSimilarVariations);
        List<String> rewindFens = concatStrings(transition.rewindFens(), synced.rewindFens());
        skippedSimilarVariationCount += transition.skippedSimilarVariations();
        applySync(synced);
        return new MoveResponse(cursor, solved, solved ? StepResult.COMPLETED : StepResult.CORRECT,
                Move.NO_MOVE, synced.autoPlayedMoves(), rewindFens,
                transition.skippedSimilarVariations() + synced.skippedSimilarVariations(), snapshot());
    }

    /**
     * Returns a hint for the next expected user move.
     *
     * @param skipSimilarVariations true to skip duplicate branch continuations
     * @return hint response
     */
    public Hint hint(boolean skipSimilarVariations) {
        SyncResult synced = sync(cursor, skipSimilarVariations);
        applySync(synced);
        PuzzleNode expected = solved ? null : nextUserMoveNode(cursor);
        if (expected == null) {
            return new Hint(Move.NO_MOVE, (byte) -1, snapshot(), true);
        }
        hintCount++;
        return new Hint(expected.move(), Move.getFromIndex(expected.move()), snapshot(), false);
    }

    /**
     * Returns whether a dropped piece should be allowed to continue to the
     * normal board move or promotion picker.
     *
     * @param move drop candidate
     * @return true when the drop matches the expected move origin/target
     */
    public boolean acceptsDrop(short move) {
        short expected = expectedMove();
        if (expected == Move.NO_MOVE || move == Move.NO_MOVE) {
            return false;
        }
        return expected == move
                || (Move.isPromotion(expected)
                && Move.getFromIndex(expected) == Move.getFromIndex(move)
                && Move.getToIndex(expected) == Move.getToIndex(move));
    }

    /**
     * Returns the current expected move.
     *
     * @return expected move, or {@link Move#NO_MOVE}
     */
    public short expectedMove() {
        PuzzleNode expected = solved ? null : nextUserMoveNode(cursor);
        return expected == null ? Move.NO_MOVE : expected.move();
    }

    /**
     * Returns the current expected SAN move.
     *
     * @return expected SAN text, or an empty string
     */
    public String expectedSan() {
        PuzzleNode expected = solved ? null : nextUserMoveNode(cursor);
        return expected == null ? "" : expected.san();
    }

    /**
     * Returns a compact solution preview from the current cursor to the line end.
     *
     * @return remaining solution line
     */
    public String solutionLine() {
        List<Integer> line = lineAt(cursor.lineIndex());
        List<String> parts = new ArrayList<>();
        for (int index = cursor.cursorIndex() + 1; index < line.size(); index++) {
            PuzzleNode node = nodeById.get(line.get(index));
            if (node != null && node.move() != Move.NO_MOVE) {
                parts.add(node.actor() == MoveActor.OPPONENT ? "..." + node.san() : node.san());
            }
        }
        return String.join(" ", parts);
    }

    /**
     * Returns a snapshot of the active state.
     *
     * @return current snapshot
     */
    public Snapshot snapshot() {
        List<Integer> line = lineAt(cursor.lineIndex());
        int nodeId = line.get(Math.min(cursor.cursorIndex(), line.size() - 1)).intValue();
        PuzzleNode node = nodeById.getOrDefault(Integer.valueOf(nodeId), nodeById.get(Integer.valueOf(ROOT_ID)));
        return new Snapshot(node.id(), node.fenAfter(), whiteToMove(node.fenAfter()), mode,
                cursor.lineIndex(), lines.size(), solved ? lines.size() : cursor.lineIndex(), solved);
    }

    /**
     * Returns the active FEN.
     *
     * @return current FEN
     */
    public String currentFen() {
        return snapshot().fen();
    }

    /**
     * Returns the puzzle title.
     *
     * @return puzzle title
     */
    public String title() {
        return title;
    }

    /**
     * Returns the source label.
     *
     * @return source label
     */
    public String source() {
        return source;
    }

    /**
     * Returns the start FEN.
     *
     * @return root FEN
     */
    public String startFen() {
        return startFen;
    }

    /**
     * Returns whether the solver plays white.
     *
     * @return true when the solver side is white
     */
    public boolean userWhite() {
        return userWhite;
    }

    /**
     * Returns normalized puzzle nodes.
     *
     * @return immutable node list
     */
    public List<PuzzleNode> nodes() {
        return nodes;
    }

    /**
     * Returns the branch count.
     *
     * @return branch count
     */
    public int totalLines() {
        return lines.size();
    }

    /**
     * Returns wrong-move count.
     *
     * @return wrong-move count
     */
    public int wrongMoveCount() {
        return wrongMoveCount;
    }

    /**
     * Returns hint count.
     *
     * @return hint count
     */
    public int hintCount() {
        return hintCount;
    }

    /**
     * Returns reveal count.
     *
     * @return reveal count
     */
    public int revealCount() {
        return revealCount;
    }

    /**
     * Returns skipped duplicate-branch count.
     *
     * @return skipped branch count
     */
    public int skippedSimilarVariationCount() {
        return skippedSimilarVariationCount;
    }

    /**
     * Returns the active cursor.
     *
     * @return current cursor
     */
    public Cursor cursor() {
        return cursor;
    }

    /**
     * Returns whether all branches are solved.
     *
     * @return true when complete
     */
    public boolean solved() {
        return solved;
    }

    /**
     * Applies a sync result to mutable counters.
     *
     * @param result sync result
     */
    private void applySync(SyncResult result) {
        cursor = result.cursor();
        solved = result.solved();
        skippedSimilarVariationCount += result.skippedSimilarVariations();
    }

    /**
     * Creates a completed response without advancing.
     *
     * @param expected expected move
     * @param syncResult sync metadata
     * @return completed response
     */
    private MoveResponse completedResponse(short expected, SyncResult syncResult) {
        return new MoveResponse(cursor, true, StepResult.COMPLETED, expected,
                syncResult.autoPlayedMoves(), syncResult.rewindFens(),
                syncResult.skippedSimilarVariations(), snapshot());
    }

    /**
     * Builds all traversable puzzle lines.
     *
     * @return line list
     */
    private List<List<Integer>> buildLines() {
        List<List<Integer>> built = new ArrayList<>();
        walkLines(ROOT_ID, List.of(Integer.valueOf(ROOT_ID)), built);
        return built;
    }

    /**
     * Recursively walks the move tree.
     *
     * @param nodeId current node identifier
     * @param path current path
     * @param built output line list
     */
    private void walkLines(int nodeId, List<Integer> path, List<List<Integer>> built) {
        List<PuzzleNode> children = childrenByParent.getOrDefault(Integer.valueOf(nodeId), List.of());
        if (children.isEmpty()) {
            built.add(path);
            return;
        }
        PuzzleNode node = nodeById.get(Integer.valueOf(nodeId));
        if (node == null) {
            return;
        }
        if (whiteToMove(node.fenAfter()) == userWhite || mode == VariationMode.MAINLINE) {
            PuzzleNode child = mainlineChild(children);
            walkLines(child.id(), append(path, child.id()), built);
            return;
        }
        for (PuzzleNode child : children) {
            walkLines(child.id(), append(path, child.id()), built);
        }
    }

    /**
     * Returns the preferred mainline child.
     *
     * @param children candidate children
     * @return selected child
     */
    private static PuzzleNode mainlineChild(List<PuzzleNode> children) {
        for (PuzzleNode child : children) {
            if (child.mainline()) {
                return child;
            }
        }
        return children.get(0);
    }

    /**
     * Returns the next user move node for a cursor.
     *
     * @param value cursor to inspect
     * @return next user node, or null
     */
    private PuzzleNode nextUserMoveNode(Cursor value) {
        List<Integer> line = lineAt(value.lineIndex());
        int cursorIndex = Math.min(value.cursorIndex(), line.size() - 1);
        PuzzleNode current = nodeById.get(line.get(cursorIndex));
        if (current == null || whiteToMove(current.fenAfter()) != userWhite) {
            return null;
        }
        if (cursorIndex + 1 >= line.size()) {
            return null;
        }
        return nodeById.get(line.get(cursorIndex + 1));
    }

    /**
     * Synchronizes a cursor to the next user decision.
     *
     * @param input input cursor
     * @param skipSimilarVariations true to skip duplicate continuations
     * @return sync result
     */
    private SyncResult sync(Cursor input, boolean skipSimilarVariations) {
        Cursor work = normalize(input);
        List<Short> autoPlayed = new ArrayList<>();
        List<String> rewindFens = new ArrayList<>();
        int skippedSimilar = 0;
        while (true) {
            List<Integer> line = lineAt(work.lineIndex());
            PuzzleNode current = nodeById.get(line.get(work.cursorIndex()));
            if (current == null) {
                return new SyncResult(work, true, List.copyOf(autoPlayed), List.copyOf(rewindFens), skippedSimilar);
            }
            while (work.cursorIndex() + 1 < line.size()) {
                PuzzleNode lineNode = nodeById.get(line.get(work.cursorIndex()));
                if (lineNode == null || whiteToMove(lineNode.fenAfter()) == userWhite) {
                    break;
                }
                PuzzleNode nextNode = nodeById.get(line.get(work.cursorIndex() + 1));
                work = new Cursor(work.lineIndex(), work.cursorIndex() + 1);
                if (nextNode != null && nextNode.move() != Move.NO_MOVE) {
                    autoPlayed.add(Short.valueOf(nextNode.move()));
                }
            }
            line = lineAt(work.lineIndex());
            boolean atLineEnd = work.cursorIndex() >= line.size() - 1;
            if (!atLineEnd) {
                if (skipSimilarVariations && hasCompletedEquivalentPosition(work.lineIndex(), work.cursorIndex())) {
                    Transition transition = nextLineTransition(work.lineIndex(), work.cursorIndex(), true);
                    skippedSimilar += transition.skippedSimilarVariations() + 1;
                    if (transition.nextLineIndex() < 0) {
                        return new SyncResult(work, true, List.copyOf(autoPlayed), List.copyOf(rewindFens),
                                skippedSimilar);
                    }
                    rewindFens.addAll(transition.rewindFens());
                    work = new Cursor(transition.nextLineIndex(), transition.targetCursorIndex());
                    continue;
                }
                return new SyncResult(work, false, List.copyOf(autoPlayed), List.copyOf(rewindFens), skippedSimilar);
            }
            Transition transition = nextLineTransition(work.lineIndex(), work.cursorIndex(), skipSimilarVariations);
            skippedSimilar += transition.skippedSimilarVariations();
            if (transition.nextLineIndex() < 0) {
                return new SyncResult(work, true, List.copyOf(autoPlayed), List.copyOf(rewindFens), skippedSimilar);
            }
            rewindFens.addAll(transition.rewindFens());
            work = new Cursor(transition.nextLineIndex(), transition.targetCursorIndex());
        }
    }

    /**
     * Returns the transition to the next branch.
     *
     * @param referenceLineIndex current line index
     * @param previousCursorIndex current cursor index
     * @param skipSimilarVariations true to skip duplicate continuations
     * @return branch transition
     */
    private Transition nextLineTransition(int referenceLineIndex, int previousCursorIndex,
            boolean skipSimilarVariations) {
        List<Integer> referenceLine = lineAt(referenceLineIndex);
        int candidateLineIndex = referenceLineIndex + 1;
        int skippedSimilar = 0;
        while (skipSimilarVariations && candidateLineIndex < lines.size()) {
            List<Integer> candidateLine = lineAt(candidateLineIndex);
            int targetCursorIndex = Math.max(0, commonPrefixLength(referenceLine, candidateLine) - 1);
            String referenceSignature = remainingUserMoveSignature(referenceLine, targetCursorIndex);
            String candidateSignature = remainingUserMoveSignature(candidateLine, targetCursorIndex);
            if (!referenceSignature.equals(candidateSignature)) {
                break;
            }
            skippedSimilar++;
            candidateLineIndex++;
        }
        if (candidateLineIndex >= lines.size()) {
            return new Transition(-1, previousCursorIndex, List.of(), skippedSimilar);
        }
        List<Integer> nextLine = lineAt(candidateLineIndex);
        int targetCursorIndex = Math.max(0, commonPrefixLength(referenceLine, nextLine) - 1);
        List<String> rewindFens = new ArrayList<>();
        if (previousCursorIndex > targetCursorIndex) {
            for (int index = previousCursorIndex - 1; index >= targetCursorIndex; index--) {
                PuzzleNode node = nodeById.get(referenceLine.get(index));
                if (node != null) {
                    rewindFens.add(node.fenAfter());
                }
            }
        }
        return new Transition(candidateLineIndex, targetCursorIndex, List.copyOf(rewindFens), skippedSimilar);
    }

    /**
     * Returns whether an equivalent decision was already completed.
     *
     * @param lineIndex candidate line index
     * @param cursorIndex candidate cursor index
     * @return true when an equivalent previous line exists
     */
    private boolean hasCompletedEquivalentPosition(int lineIndex, int cursorIndex) {
        String candidate = positionContinuationSignature(lineIndex, cursorIndex);
        if (candidate == null) {
            return false;
        }
        for (int completedLineIndex = 0; completedLineIndex < lineIndex; completedLineIndex++) {
            List<Integer> completedLine = lineAt(completedLineIndex);
            for (int completedCursorIndex = 0; completedCursorIndex < completedLine.size(); completedCursorIndex++) {
                if (decisionCursor(completedLine, completedCursorIndex)
                        && candidate.equals(positionContinuationSignature(completedLineIndex, completedCursorIndex))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a continuation signature for duplicate-branch detection.
     *
     * @param lineIndex line index
     * @param cursorIndex cursor index
     * @return signature, or null
     */
    private String positionContinuationSignature(int lineIndex, int cursorIndex) {
        List<Integer> line = lineAt(lineIndex);
        PuzzleNode node = nodeById.get(line.get(Math.min(cursorIndex, line.size() - 1)));
        if (node == null) {
            return null;
        }
        return fenStateKey(node.fenAfter()) + "::" + remainingUserMoveSignature(line, cursorIndex);
    }

    /**
     * Returns the remaining user move signature from one line index.
     *
     * @param line line node identifiers
     * @param cursorIndex cursor index
     * @return signature
     */
    private String remainingUserMoveSignature(List<Integer> line, int cursorIndex) {
        List<String> moves = new ArrayList<>();
        for (int index = cursorIndex + 1; index < line.size(); index++) {
            PuzzleNode node = nodeById.get(line.get(index));
            if (node != null && node.actor() == MoveActor.USER && !node.uci().isBlank()) {
                moves.add(node.uci().toLowerCase(Locale.ROOT));
            }
        }
        return String.join("|", moves);
    }

    /**
     * Returns whether a cursor is a solver decision or branch end.
     *
     * @param line line node identifiers
     * @param cursorIndex cursor index
     * @return true when the cursor is a decision
     */
    private boolean decisionCursor(List<Integer> line, int cursorIndex) {
        PuzzleNode node = nodeById.get(line.get(Math.min(cursorIndex, line.size() - 1)));
        return node != null && (cursorIndex >= line.size() - 1 || whiteToMove(node.fenAfter()) == userWhite);
    }

    /**
     * Normalizes a cursor into the current line bounds.
     *
     * @param value candidate cursor
     * @return normalized cursor
     */
    private Cursor normalize(Cursor value) {
        int lineIndex = value == null ? 0 : Math.max(0, Math.min(value.lineIndex(), lines.size() - 1));
        List<Integer> line = lineAt(lineIndex);
        int cursorIndex = value == null ? 0 : Math.max(0, Math.min(value.cursorIndex(), line.size() - 1));
        return new Cursor(lineIndex, cursorIndex);
    }

    /**
     * Returns a line by index.
     *
     * @param lineIndex line index
     * @return line node identifiers
     */
    private List<Integer> lineAt(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= lines.size()) {
            return List.of(Integer.valueOf(ROOT_ID));
        }
        return lines.get(lineIndex);
    }

    /**
     * Returns a FEN key ignoring halfmove and fullmove counters.
     *
     * @param fen FEN
     * @return comparable key
     */
    private static String fenStateKey(String fen) {
        String[] parts = fen.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(4, parts.length); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(parts[i]);
        }
        return builder.toString();
    }

    /**
     * Reads side to move from a FEN.
     *
     * @param fen FEN string
     * @return true when white is to move
     */
    private static boolean whiteToMove(String fen) {
        String[] parts = fen == null ? new String[0] : fen.trim().split("\\s+");
        return parts.length < 2 || !"b".equals(parts[1]);
    }

    /**
     * Returns a list with one identifier appended.
     *
     * @param path source path
     * @param id node identifier
     * @return appended path
     */
    private static List<Integer> append(List<Integer> path, int id) {
        List<Integer> next = new ArrayList<>(path);
        next.add(Integer.valueOf(id));
        return List.copyOf(next);
    }

    /**
     * Computes common prefix length.
     *
     * @param a first line
     * @param b second line
     * @return common prefix length
     */
    private static int commonPrefixLength(List<Integer> a, List<Integer> b) {
        int limit = Math.min(a.size(), b.size());
        int index = 0;
        while (index < limit && a.get(index).equals(b.get(index))) {
            index++;
        }
        return index;
    }

    /**
     * Concatenates move lists.
     *
     * @param first first list
     * @param second second list
     * @return combined immutable list
     */
    private static List<Short> concatMoves(List<Short> first, List<Short> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        List<Short> combined = new ArrayList<>(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }

    /**
     * Concatenates string lists.
     *
     * @param first first list
     * @param second second list
     * @return combined immutable list
     */
    private static List<String> concatStrings(List<String> first, List<String> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        List<String> combined = new ArrayList<>(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }

    /**
     * Internal normalized tree builder.
     */
    private static final class TreeBuilder {

        /**
         * Whether the solver plays white.
         */
        private final boolean userWhite;

        /**
         * Normalized output nodes.
         */
        private final List<PuzzleNode> nodes = new ArrayList<>();

        /**
         * Sibling counters by parent node identifier.
         */
        private final Map<Integer, Integer> siblingCounters = new HashMap<>();

        /**
         * Next node identifier.
         */
        private int nextId = ROOT_ID + 1;

        /**
         * Creates a builder with a root node.
         *
         * @param userWhite whether the solver plays white
         * @param rootFen root FEN
         */
        TreeBuilder(boolean userWhite, String rootFen) {
            this.userWhite = userWhite;
            nodes.add(new PuzzleNode(ROOT_ID, PuzzleNode.NO_PARENT, 0, "", Move.NO_MOVE, "",
                    MoveActor.OPPONENT, true, 0, rootFen));
        }

        /**
         * Returns normalized nodes.
         *
         * @return immutable node list
         */
        List<PuzzleNode> nodes() {
            return Collections.unmodifiableList(nodes);
        }

        /**
         * Processes one PGN sequence.
         *
         * @param node first PGN node
         * @param parentId parent identifier
         * @param startPly ply before the sequence
         * @param start position before the sequence
         * @param mainline true when this sequence is on the mainline
         */
        void processLine(Game.Node node, int parentId, int startPly, Position start, boolean mainline) {
            Position cursor = start.copy();
            Game.Node current = node;
            int currentParentId = parentId;
            int currentPly = startPly;
            while (current != null) {
                Position before = cursor.copy();
                short move = SAN.fromAlgebraic(cursor, current.getSan());
                Position next = cursor.copy();
                next.play(move);
                int id = nextId++;
                PuzzleNode normalized = new PuzzleNode(id, currentParentId, currentPly + 1,
                        PositionText.safeSan(before, move), move, Move.toString(move),
                        before.isWhiteToMove() == userWhite ? MoveActor.USER : MoveActor.OPPONENT,
                        mainline, nextSiblingOrder(currentParentId), next.toString());
                nodes.add(normalized);
                for (Game.Node variation : current.getVariations()) {
                    processLine(variation, currentParentId, currentPly, before, false);
                }
                currentParentId = id;
                currentPly++;
                cursor = next;
                current = current.getNext();
            }
        }

        /**
         * Returns the next sibling order for a parent.
         *
         * @param parentId parent identifier
         * @return sibling order
         */
        private int nextSiblingOrder(int parentId) {
            Integer key = Integer.valueOf(parentId);
            int order = siblingCounters.getOrDefault(key, Integer.valueOf(0)).intValue();
            siblingCounters.put(key, Integer.valueOf(order + 1));
            return order;
        }
    }

    /**
     * Sync metadata.
     *
     * @param cursor synced cursor
     * @param solved true when complete
     * @param autoPlayedMoves auto-played opponent moves
     * @param rewindFens branch rewind FENs
     * @param skippedSimilarVariations duplicate branches skipped
     */
    private record SyncResult(
            Cursor cursor,
            boolean solved,
            List<Short> autoPlayedMoves,
            List<String> rewindFens,
            int skippedSimilarVariations) { }

    /**
     * Branch transition metadata.
     *
     * @param nextLineIndex next line index, or -1 when complete
     * @param targetCursorIndex cursor index in the next line
     * @param rewindFens branch rewind FENs
     * @param skippedSimilarVariations duplicate branches skipped
     */
    private record Transition(
            int nextLineIndex,
            int targetCursorIndex,
            List<String> rewindFens,
            int skippedSimilarVariations) { }
}
