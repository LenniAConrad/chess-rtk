package application.gui.workbench.game;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.struct.Game;
import chess.struct.Pgn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.table.AbstractTableModel;

/**
 * Tracks the workbench game line and exposes it as a move-history table.
 */
public final class GameModel extends AbstractTableModel {

    /**
     * Serialization identifier for Swing table model compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Ply label column index.
     */
    private static final int COL_PLY = 0;

    /**
     * SAN column index.
     */
    private static final int COL_SAN = 1;

    /**
     * UCI column index.
     */
    private static final int COL_UCI = 2;

    /**
     * FEN column index.
     */
    private static final int COL_FEN = 3;

    /**
     * Column names.
     */
    private static final String[] COLUMNS = { "Ply", "SAN", "UCI", "FEN" };

    /**
     * Positions from root through current line end.
     */
    private final List<Position> positions = new ArrayList<>();

    /**
     * Move rows for the main line.
     */
    private final List<MoveRow> moves = new ArrayList<>();

    /**
     * Rows shown in the history table, including imported variation moves.
     */
    private final List<MoveRow> displayRows = new ArrayList<>();

    /**
     * Display-row index for each mainline ply. Index zero is the root and maps
     * to {@code -1}.
     */
    private final List<Integer> mainlineRows = new ArrayList<>();

    /**
     * Imported game tree preserved for PGN export while no edit has replaced
     * the line.
     */
    private Game importedGame;

    /**
     * Per-ply PGN comment bodies for the clean main line (ply 0 = root, which
     * serializes as a preamble comment). This is where board annotations bind to
     * a position: the encoded {@code [%cal]}/{@code [%csl]}/… directives live
     * here and flow into {@link #pgn()}. Only used while the line is the simple
     * main line (no imported tree / variations); see {@link #currentNodeComment}.
     */
    private final Map<Integer, String> plyComments = new HashMap<>();

    /**
     * Currently selected ply, where zero means the root.
     */
    private int currentPly;

    /**
     * Currently selected display row, or {@code -1} for the root position.
     */
    private int currentRow = -1;

    /**
     * Creates an empty game model.
     */
    public GameModel() {
        reset(new Position(Setup.getStandardStartFEN()));
    }

    /**
     * Starts a new game from a root position.
     *
     * @param start root position
     */
    public void reset(Position start) {
        positions.clear();
        moves.clear();
        displayRows.clear();
        mainlineRows.clear();
        mainlineRows.add(-1);
        importedGame = null;
        plyComments.clear();
        positions.add(start.copy());
        currentPly = 0;
        currentRow = -1;
        fireTableDataChanged();
    }

    /**
     * Appends a move at the current ply, truncating any future line first.
     *
     * @param before position before the move
     * @param move encoded move
     * @param after position after the move
     */
    public void append(Position before, short move, Position after) {
        List<Short> basePath = currentPath();
        if (importedGame != null || isVariationSelection() || currentPly < moves.size()) {
            ensureEditableGame();
            List<Short> selectedPath = appendToGameTree(before, move, basePath);
            rebuildFromImportedGameSelecting(selectedPath);
            return;
        }
        truncateAfterCurrentPly();
        moves.add(new MoveRow(plyLabel(before), PositionText.safeSan(before, move),
                Move.toString(move), after.toString(), move, after.copy(),
                pathWithMove(currentMainlinePath(), move), true, 0));
        positions.add(after.copy());
        importedGame = null;
        rebuildMainlineDisplayRows();
        currentPly = moves.size();
        currentRow = rowForMainlinePly(currentPly);
        fireTableDataChanged();
    }

    /**
     * Replaces the game line with a validated move sequence.
     *
     * @param start root position
     * @param line encoded moves
     */
    public void loadLine(Position start, List<Short> line) {
        positions.clear();
        moves.clear();
        displayRows.clear();
        mainlineRows.clear();
        mainlineRows.add(-1);
        importedGame = null;
        plyComments.clear();
        positions.add(start.copy());
        Position cursor = start.copy();
        List<Short> path = new ArrayList<>();
        for (Short boxed : line) {
            if (boxed == null) {
                continue;
            }
            short move = boxed.shortValue();
            Position next = cursor.copy();
            next.play(move);
            path.add(move);
            moves.add(new MoveRow(plyLabel(cursor), PositionText.safeSan(cursor, move), Move.toString(move),
                    next.toString(), move, next.copy(), List.copyOf(path), true, 0));
            positions.add(next.copy());
            cursor = next;
        }
        rebuildMainlineDisplayRows();
        currentPly = moves.size();
        currentRow = rowForMainlinePly(currentPly);
        fireTableDataChanged();
    }

    /**
     * Replaces the model with a parsed PGN game, preserving root and nested
     * variation rows for display and later PGN export.
     *
     * @param start root position
     * @param game parsed game
     */
    public void loadGame(Position start, Game game) {
        Position root = start == null ? new Position(Setup.getStandardStartFEN()) : start.copy();
        positions.clear();
        moves.clear();
        displayRows.clear();
        mainlineRows.clear();
        mainlineRows.add(-1);
        positions.add(root.copy());
        importedGame = game;
        loadMainlineMoves(root, game == null ? null : game.getMainline());
        appendGameDisplayRows(root, game);
        currentPly = moves.size();
        currentRow = rowForMainlinePly(currentPly);
        fireTableDataChanged();
    }

    /**
     * Moves the current pointer to a ply.
     *
     * @param ply requested ply
     */
    public void jumpToPly(int ply) {
        int next = Math.max(0, Math.min(ply, moves.size()));
        int nextRow = rowForMainlinePly(next);
        if (next == currentPly && currentRow == nextRow) {
            return;
        }
        currentPly = next;
        currentRow = nextRow;
    }

    /**
     * Moves the current pointer to a visible history row, including variation
     * rows imported from PGN.
     *
     * @param row requested row
     */
    public void jumpToRow(int row) {
        if (row < 0 || row >= displayRows.size()) {
            currentPly = 0;
            currentRow = -1;
            return;
        }
        MoveRow selected = displayRows.get(row);
        currentRow = row;
        currentPly = selected.path().size();
    }

    /**
     * Moves through the currently inspected line by one or more plies.
     *
     * <p>
     * Mainline selections continue to use mainline ply navigation. Imported
     * variation selections follow their own visible continuation instead of
     * jumping sideways to the mainline at the same ply depth.
     * </p>
     *
     * @param delta relative ply delta
     * @return true when the current position changed
     */
    public boolean navigate(int delta) {
        if (delta == 0) {
            return false;
        }
        boolean changed = false;
        int remaining = delta;
        while (remaining != 0) {
            boolean stepChanged = remaining > 0 ? navigateForwardOne() : navigateBackwardOne();
            if (!stepChanged) {
                return changed;
            }
            changed = true;
            remaining += remaining > 0 ? -1 : 1;
        }
        return changed;
    }

    /**
     * Returns whether there is a previous ply.
     *
     * @return true when the line can navigate backward
     */
    public boolean canBack() {
        return currentPly > 0;
    }

    /**
     * Returns whether there is a next ply.
     *
     * @return true when the line can navigate forward
     */
    public boolean canForward() {
        if (isVariationSelection()) {
            return nextVariationContinuationRow() >= 0;
        }
        return currentPly < moves.size();
    }

    /**
     * Returns the current ply.
     *
     * @return current ply
     */
    public int currentPly() {
        return currentPly;
    }

    /**
     * Returns the final ply in the current line.
     *
     * @return line length in plies
     */
    public int lastPly() {
        return moves.size();
    }

    /**
     * Returns the current position.
     *
     * @return current position copy
     */
    public Position currentPosition() {
        if (currentRow >= 0 && currentRow < displayRows.size()) {
            return displayRows.get(currentRow).position().copy();
        }
        return positions.get(currentPly).copy();
    }

    /**
     * Returns the root position.
     *
     * @return root position copy
     */
    public Position startPosition() {
        return positions.get(0).copy();
    }

    /**
     * Returns the last move that led to the current ply.
     *
     * @return encoded move, or {@link Move#NO_MOVE} at the root
     */
    public short currentLastMove() {
        if (currentRow >= 0 && currentRow < displayRows.size()) {
            return displayRows.get(currentRow).move();
        }
        return currentPly == 0 ? Move.NO_MOVE : moves.get(currentPly - 1).move();
    }

    /**
     * Returns the table row for the current ply.
     *
     * @return row index, or -1 at root
     */
    public int currentRow() {
        return currentRow;
    }

    /**
     * Returns the move path from the root to the currently selected row.
     *
     * <p>
     * Imported PGN variation rows keep their own path, while ordinary mainline
     * navigation returns the editable mainline prefix through
     * {@link #currentPly()}.
     * </p>
     *
     * @return immutable current move path
     */
    public List<Short> currentPath() {
        if (currentRow >= 0 && currentRow < displayRows.size()) {
            return List.copyOf(displayRows.get(currentRow).path());
        }
        return List.copyOf(currentMainlinePath());
    }

    /**
     * Returns the number of visible imported variation rows.
     *
     * @return variation row count
     */
    public int variationRowCount() {
        int count = 0;
        for (MoveRow row : displayRows) {
            if (!row.mainline()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the SAN move line.
     *
     * @return SAN line
     */
    public String sanLine() {
        StringBuilder sb = new StringBuilder();
        Position cursor = startPosition();
        for (MoveRow row : moves) {
            appendMovePrefix(sb, cursor);
            sb.append(row.san()).append(' ');
            cursor.play(row.move());
        }
        return sb.toString().trim();
    }

    /**
     * Returns the UCI move line.
     *
     * @return UCI line
     */
    public String uciLine() {
        List<String> parts = new ArrayList<>();
        for (MoveRow row : moves) {
            parts.add(row.uci());
        }
        return String.join(" ", parts);
    }

    /**
     * Returns all positions in the game line as newline-delimited FENs.
     *
     * @return FEN list
     */
    public String fenList() {
        StringBuilder sb = new StringBuilder();
        for (Position position : positions) {
            if (!sb.isEmpty()) {
                sb.append(System.lineSeparator());
            }
            sb.append(position);
        }
        return sb.toString();
    }

    /**
     * Returns immutable snapshots for each mainline ply.
     *
     * <p>
     * Each snapshot carries the position before and after the move, so review,
     * study, and export panels can inspect a game without depending on the
     * table model's private row storage.
     * </p>
     *
     * @return mainline ply snapshots in move order
     */
    public List<PlySnapshot> mainlineSnapshots() {
        List<PlySnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            MoveRow row = moves.get(i);
            Position before = positions.get(i);
            snapshots.add(new PlySnapshot(i + 1, row.san(), row.uci(), before.toString(),
                    row.fen(), row.move(), before.isWhiteToMove()));
        }
        return List.copyOf(snapshots);
    }

    /**
     * Returns immutable snapshots for every visible move-history row.
     *
     * <p>
     * Unlike {@link #mainlineSnapshots()}, this includes imported PGN variation
     * rows with their branch depth and table row index. Analyze-side tree views
     * use this to render Lichess-style branches while preserving the existing
     * {@link #jumpToRow(int)} navigation path.
     * </p>
     *
     * @return visible move-row snapshots in display order
     */
    public List<VisibleMoveSnapshot> visibleMoveSnapshots() {
        List<VisibleMoveSnapshot> snapshots = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < displayRows.size(); rowIndex++) {
            MoveRow row = displayRows.get(rowIndex);
            snapshots.add(new VisibleMoveSnapshot(rowIndex, row.ply().stripLeading(),
                    row.san().stripLeading(), row.uci(), row.fen(), row.path().size(),
                    row.mainline(), row.variationDepth(), uciPath(row.path())));
        }
        return List.copyOf(snapshots);
    }

    /**
     * Returns the current game line as PGN.
     *
     * @return PGN text
     */
    public String pgn() {
        if (importedGame != null) {
            return Pgn.toPgn(importedGame);
        }
        Game game = new Game();
        game.setStartPosition(startPosition());
        game.putTag("Event", "ChessRTK Workbench");
        game.putTag("Site", "?");
        game.putTag("Result", "*");
        game.setResult("*");
        String rootComment = plyComments.get(Integer.valueOf(0));
        if (rootComment != null && !rootComment.isBlank()) {
            game.addPreambleComment(rootComment);
        }
        Game.Node head = null;
        Game.Node previous = null;
        int ply = 1;
        for (MoveRow row : moves) {
            Game.Node node = new Game.Node(row.san());
            String comment = plyComments.get(Integer.valueOf(ply));
            if (comment != null && !comment.isBlank()) {
                node.setCommentsAfter(List.of(comment));
            }
            if (head == null) {
                head = node;
            } else {
                previous.setNext(node);
            }
            previous = node;
            ply++;
        }
        game.setMainline(head);
        return Pgn.toPgn(game);
    }

    /**
     * Returns the PGN comment body bound to the current position (the encoded
     * board-annotation directives plus any free text), or an empty string. Only
     * the simple main line is tracked here; an imported tree / variation
     * selection returns empty (its comments live on the tree).
     *
     * @return current position's comment body
     */
    public String currentNodeComment() {
        return importedGame == null ? plyComments.getOrDefault(Integer.valueOf(currentPly), "") : "";
    }

    /**
     * Binds a PGN comment body to the current position (used to persist board
     * annotations onto the move node). A no-op while an imported tree / variation
     * line is active, so it never corrupts that representation.
     *
     * @param comment comment body, or null/blank to clear
     */
    public void setCurrentNodeComment(String comment) {
        if (importedGame != null) {
            return;
        }
        Integer key = Integer.valueOf(currentPly);
        if (comment == null || comment.isBlank()) {
            plyComments.remove(key);
        } else {
            plyComments.put(key, comment);
        }
    }

    /**
     * Returns the row count.
     *
     * @return row count
     */
    @Override
    public int getRowCount() {
        return displayRows.size();
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
     * Returns a table cell value.
     *
     * @param rowIndex zero-based row index
     * @param columnIndex zero-based column index
     * @return cell value
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= displayRows.size()) {
            return "";
        }
        MoveRow row = displayRows.get(rowIndex);
        return switch (columnIndex) {
            case COL_PLY -> row.ply();
            case COL_SAN -> row.san();
            case COL_UCI -> row.uci();
            case COL_FEN -> row.fen();
            default -> "";
        };
    }

    /**
     * Returns whether a cell is editable.
     *
     * @param row row index
     * @param column column index
     * @return false because history rows are read-only
     */
    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    /**
     * Drops all future moves after the current ply.
     */
    private void truncateAfterCurrentPly() {
        while (moves.size() > currentPly) {
            moves.remove(moves.size() - 1);
        }
        while (positions.size() > currentPly + 1) {
            positions.remove(positions.size() - 1);
        }
        plyComments.keySet().removeIf(ply -> ply > currentPly);
        importedGame = null;
    }

    /**
     * Moves forward by one ply according to the current selection context.
     *
     * @return true when navigation changed the selected position
     */
    private boolean navigateForwardOne() {
        if (isVariationSelection()) {
            int row = nextVariationContinuationRow();
            if (row < 0) {
                return false;
            }
            jumpToRow(row);
            return true;
        }
        int previous = currentPly;
        jumpToPly(currentPly + 1);
        return currentPly != previous;
    }

    /**
     * Moves backward by one ply according to the current selection context.
     *
     * @return true when navigation changed the selected position
     */
    private boolean navigateBackwardOne() {
        if (isVariationSelection()) {
            return navigateVariationBackwardOne();
        }
        int previous = currentPly;
        jumpToPly(currentPly - 1);
        return currentPly != previous;
    }

    /**
     * Returns whether the current display row is an imported variation row.
     *
     * @return true when a non-mainline variation row is selected
     */
    private boolean isVariationSelection() {
        return currentRow >= 0
                && currentRow < displayRows.size()
                && !displayRows.get(currentRow).mainline();
    }

    /**
     * Moves one ply backward along the selected variation path.
     *
     * @return true when navigation changed the selected position
     */
    private boolean navigateVariationBackwardOne() {
        List<Short> path = displayRows.get(currentRow).path();
        if (path.isEmpty()) {
            return false;
        }
        List<Short> previousPath = path.subList(0, path.size() - 1);
        if (previousPath.isEmpty()) {
            currentPly = 0;
            currentRow = -1;
            return true;
        }
        int row = findPreviousRowForPath(previousPath, currentRow - 1);
        if (row >= 0) {
            jumpToRow(row);
            return true;
        }
        if (isMainlinePrefix(previousPath)) {
            jumpToPly(previousPath.size());
            return true;
        }
        return false;
    }

    /**
     * Finds the next visible variation row that extends the current variation
     * path by one move.
     *
     * @return display row, or {@code -1} when there is no continuation
     */
    private int nextVariationContinuationRow() {
        if (!isVariationSelection()) {
            return -1;
        }
        List<Short> path = displayRows.get(currentRow).path();
        int targetSize = path.size() + 1;
        for (int row = currentRow + 1; row < displayRows.size(); row++) {
            List<Short> candidate = displayRows.get(row).path();
            if (candidate.size() == targetSize && startsWith(candidate, path)) {
                return row;
            }
        }
        return -1;
    }

    /**
     * Finds a row whose path exactly matches the requested path.
     *
     * @param path requested move path
     * @param start inclusive starting row
     * @return matching row, or {@code -1}
     */
    private int findPreviousRowForPath(List<Short> path, int start) {
        for (int row = Math.min(start, displayRows.size() - 1); row >= 0; row--) {
            if (samePath(displayRows.get(row).path(), path)) {
                return row;
            }
        }
        return -1;
    }

    /**
     * Returns whether a path is the current editable mainline prefix.
     *
     * @param path path to check
     * @return true when the path matches the mainline prefix
     */
    private boolean isMainlinePrefix(List<Short> path) {
        if (path.size() > moves.size()) {
            return false;
        }
        for (int i = 0; i < path.size(); i++) {
            if (moves.get(i).move() != path.get(i).shortValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether a candidate path begins with a prefix.
     *
     * @param candidate candidate path
     * @param prefix prefix path
     * @return true when candidate starts with prefix
     */
    private static boolean startsWith(List<Short> candidate, List<Short> prefix) {
        if (prefix.size() > candidate.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!candidate.get(i).equals(prefix.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether two move paths are equal.
     *
     * @param left first path
     * @param right second path
     * @return true when paths have the same moves
     */
    private static boolean samePath(List<Short> left, List<Short> right) {
        return left.size() == right.size() && startsWith(left, right);
    }

    /**
     * Ensures {@link #importedGame} contains an editable tree equivalent to the
     * current visible mainline. Once a branch is added, this tree becomes the
     * source of truth for PGN export and variation-row rebuilding.
     */
    private void ensureEditableGame() {
        if (importedGame != null) {
            return;
        }
        Game game = new Game();
        game.setStartPosition(startPosition());
        game.putTag("Event", "ChessRTK Workbench");
        game.putTag("Site", "?");
        game.putTag("Result", "*");
        game.setResult("*");
        Game.Node head = null;
        Game.Node previous = null;
        for (MoveRow row : moves) {
            Game.Node node = new Game.Node(row.san());
            if (head == null) {
                head = node;
            } else {
                previous.setNext(node);
            }
            previous = node;
        }
        game.setMainline(head);
        importedGame = game;
    }

    /**
     * Appends a move into the editable game tree, creating a PGN variation when
     * the current position already has a different continuation.
     *
     * @param before position before the move
     * @param move encoded move
     * @param basePath current path before the move
     * @return path that should be selected after the edit
     */
    private List<Short> appendToGameTree(Position before, short move, List<Short> basePath) {
        List<Short> selectedPath = pathWithMove(basePath, move);
        List<Short> continuation = continuationPathForSelection();
        if (!continuation.isEmpty()) {
            short existingMove = continuation.get(continuation.size() - 1).shortValue();
            if (existingMove == move) {
                return continuation;
            }
            Game.Node anchor = findNodeByPath(continuation);
            if (anchor != null && !hasVariationStartingWith(anchor, before, move)) {
                anchor.addVariation(new Game.Node(PositionText.safeSan(before, move)));
            }
            return selectedPath;
        }
        appendContinuation(basePath, before, move);
        return selectedPath;
    }

    /**
     * Returns the existing one-ply continuation from the current selection, if
     * one exists in the selected line.
     *
     * @return continuation path, or empty list
     */
    private List<Short> continuationPathForSelection() {
        if (isVariationSelection()) {
            int row = nextVariationContinuationRow();
            return row < 0 ? List.of() : displayRows.get(row).path();
        }
        if (currentPly < moves.size()) {
            return pathWithMove(currentMainlinePath(), moves.get(currentPly).move());
        }
        return List.of();
    }

    /**
     * Appends a continuation after the supplied path.
     *
     * @param basePath path before the move
     * @param before position before the move
     * @param move encoded move
     */
    private void appendContinuation(List<Short> basePath, Position before, short move) {
        Game.Node node = new Game.Node(PositionText.safeSan(before, move));
        if (basePath.isEmpty()) {
            if (importedGame.getMainline() == null) {
                importedGame.setMainline(node);
            } else if (!hasRootVariationStartingWith(before, move)) {
                importedGame.addRootVariation(node);
            }
            return;
        }
        Game.Node parent = findNodeByPath(basePath);
        if (parent == null) {
            return;
        }
        if (parent.getNext() == null) {
            parent.setNext(node);
        } else if (!hasVariationStartingWith(parent.getNext(), before, move)) {
            parent.getNext().addVariation(node);
        }
    }

    /**
     * Rebuilds the model from {@link #importedGame} and selects a path.
     *
     * @param selectedPath path to select after rebuilding
     */
    private void rebuildFromImportedGameSelecting(List<Short> selectedPath) {
        Position root = importedGame.getStartPosition() == null
                ? new Position(Setup.getStandardStartFEN())
                : importedGame.getStartPosition().copy();
        positions.clear();
        moves.clear();
        displayRows.clear();
        mainlineRows.clear();
        mainlineRows.add(-1);
        positions.add(root.copy());
        loadMainlineMoves(root, importedGame.getMainline());
        appendGameDisplayRows(root, importedGame);
        selectPath(selectedPath);
        fireTableDataChanged();
    }

    /**
     * Selects the visible row or root for a path.
     *
     * @param path move path
     */
    private void selectPath(List<Short> path) {
        if (path == null || path.isEmpty()) {
            currentPly = 0;
            currentRow = -1;
            return;
        }
        for (int row = 0; row < displayRows.size(); row++) {
            if (samePath(displayRows.get(row).path(), path)) {
                currentPly = path.size();
                currentRow = row;
                return;
            }
        }
        if (isMainlinePrefix(path)) {
            currentPly = path.size();
            currentRow = rowForMainlinePly(currentPly);
            return;
        }
        currentPly = path.size();
        currentRow = -1;
    }

    /**
     * Finds a node whose path exactly matches the supplied path.
     *
     * @param path path to locate
     * @return matching node, or null
     */
    private Game.Node findNodeByPath(List<Short> path) {
        if (importedGame == null || path == null || path.isEmpty()) {
            return null;
        }
        Position root = importedGame.getStartPosition() == null
                ? new Position(Setup.getStandardStartFEN())
                : importedGame.getStartPosition().copy();
        Game.Node mainline = findNodeInSequence(importedGame.getMainline(), root, List.of(), path);
        if (mainline != null) {
            return mainline;
        }
        for (Game.Node variation : importedGame.getRootVariations()) {
            Game.Node found = findNodeInSequence(variation, root, List.of(), path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Finds a node in one PGN sequence.
     *
     * @param node sequence head
     * @param start sequence start position
     * @param pathPrefix path before the sequence
     * @param target target path
     * @return matching node, or null
     */
    private static Game.Node findNodeInSequence(
            Game.Node node,
            Position start,
            List<Short> pathPrefix,
            List<Short> target) {
        Position cursor = start.copy();
        List<Short> path = new ArrayList<>(pathPrefix);
        Game.Node current = node;
        while (current != null) {
            List<Short> pathBeforeMove = List.copyOf(path);
            short move;
            try {
                move = SAN.fromAlgebraic(cursor, current.getSan());
            } catch (RuntimeException ex) {
                return null;
            }
            path.add(Short.valueOf(move));
            if (samePath(path, target)) {
                return current;
            }
            for (Game.Node variation : current.getVariations()) {
                Game.Node found = findNodeInSequence(variation, cursor, pathBeforeMove, target);
                if (found != null) {
                    return found;
                }
            }
            cursor.play(move);
            current = current.getNext();
        }
        return null;
    }

    /**
     * Returns whether a node already has a variation starting with a move.
     *
     * @param anchor node whose variations should be inspected
     * @param before position before the variation move
     * @param move encoded move
     * @return true when an equivalent variation exists
     */
    private static boolean hasVariationStartingWith(Game.Node anchor, Position before, short move) {
        for (Game.Node variation : anchor.getVariations()) {
            if (nodeMoveEquals(variation, before, move)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a root variation already starts with a move.
     *
     * @param before position before the variation move
     * @param move encoded move
     * @return true when an equivalent root variation exists
     */
    private boolean hasRootVariationStartingWith(Position before, short move) {
        for (Game.Node variation : importedGame.getRootVariations()) {
            if (nodeMoveEquals(variation, before, move)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a node's SAN decodes to the supplied move.
     *
     * @param node node to inspect
     * @param before position before the node
     * @param move encoded move
     * @return true when the node represents the move
     */
    private static boolean nodeMoveEquals(Game.Node node, Position before, short move) {
        try {
            return SAN.fromAlgebraic(before, node.getSan()) == move;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Selection of which mainline positions a PGN import should yield.
     */
    public enum FenMode {
        /**
         * Every position, one FEN per ply (including the start).
         */
        ALL,

        /**
         * Only the final position of the game.
         */
        FINAL,

        /**
         * The start position and every Nth ply thereafter.
         */
        EVERY_NTH
    }

    /**
     * Walks a parsed game's mainline and returns the selected positions as FEN
     * strings. Mirrors {@link #loadMainlineMoves} so imported FENs match the
     * navigator exactly; an unparseable move stops the walk at that point.
     *
     * @param game parsed game
     * @param mode which positions to keep
     * @param everyNth ply stride for {@link FenMode#EVERY_NTH}
     * @return selected FEN strings in mainline order
     */
    public static List<String> mainlineFens(Game game, FenMode mode, int everyNth) {
        if (game == null) {
            return List.of();
        }
        Position cursor = game.getStartPosition() == null
                ? new Position(Setup.getStandardStartFEN())
                : game.getStartPosition().copy();
        List<String> all = new ArrayList<>();
        all.add(cursor.toString());
        Game.Node node = game.getMainline();
        while (node != null) {
            try {
                short move = SAN.fromAlgebraic(cursor, node.getSan());
                Position next = cursor.copy();
                next.play(move);
                all.add(next.toString());
                cursor = next;
            } catch (RuntimeException ex) {
                break;
            }
            node = node.getNext();
        }
        return switch (mode) {
            case FINAL -> all.isEmpty() ? List.of() : List.of(all.get(all.size() - 1));
            case EVERY_NTH -> everyNthFen(all, Math.max(1, everyNth));
            case ALL -> List.copyOf(all);
        };
    }

    /**
     * Keeps the first FEN and every {@code stride}-th FEN thereafter.
     *
     * @param all every mainline FEN
     * @param stride ply stride
     * @return sampled FENs
     */
    private static List<String> everyNthFen(List<String> all, int stride) {
        List<String> sampled = new ArrayList<>();
        for (int i = 0; i < all.size(); i += stride) {
            sampled.add(all.get(i));
        }
        return List.copyOf(sampled);
    }

    /**
     * Loads the PGN mainline into the navigation arrays.
     *
     * @param start root position
     * @param node first mainline node
     */
    private void loadMainlineMoves(Position start, Game.Node node) {
        Position cursor = start.copy();
        List<Short> path = new ArrayList<>();
        Game.Node current = node;
        while (current != null) {
            short move = SAN.fromAlgebraic(cursor, current.getSan());
            Position next = cursor.copy();
            next.play(move);
            path.add(move);
            moves.add(new MoveRow(plyLabel(cursor), PositionText.safeSan(cursor, move), Move.toString(move),
                    next.toString(), move, next.copy(), List.copyOf(path), true, 0));
            positions.add(next.copy());
            cursor = next;
            current = current.getNext();
        }
    }

    /**
     * Builds visible rows from a parsed game tree.
     *
     * @param start root position
     * @param game parsed game
     */
    private void appendGameDisplayRows(Position start, Game game) {
        if (game == null) {
            rebuildMainlineDisplayRows();
            return;
        }
        for (Game.Node rootVariation : game.getRootVariations()) {
            appendSequenceRows(start, rootVariation, 1, List.of(), false);
        }
        Position cursor = start.copy();
        List<Short> path = new ArrayList<>();
        Game.Node current = game.getMainline();
        int mainlinePly = 0;
        while (current != null) {
            List<Short> pathBeforeMove = List.copyOf(path);
            Position before = cursor.copy();
            short move = SAN.fromAlgebraic(cursor, current.getSan());
            Position next = cursor.copy();
            next.play(move);
            path.add(move);
            mainlinePly++;
            mainlineRows.add(displayRows.size());
            displayRows.add(new MoveRow(plyLabel(before), PositionText.safeSan(before, move), Move.toString(move),
                    next.toString(), move, next.copy(), List.copyOf(path), true, 0));
            for (Game.Node variation : current.getVariations()) {
                appendSequenceRows(before, variation, 1, pathBeforeMove, false);
            }
            cursor = next;
            current = current.getNext();
        }
        if (mainlinePly == 0) {
            currentRow = -1;
        }
    }

    /**
     * Appends visible rows for one variation sequence.
     *
     * @param start sequence start position
     * @param node first node
     * @param depth variation nesting depth
     * @param pathPrefix path to the sequence start
     * @param mainline true for mainline rows
     */
    private void appendSequenceRows(Position start, Game.Node node, int depth, List<Short> pathPrefix,
            boolean mainline) {
        Position cursor = start.copy();
        List<Short> path = new ArrayList<>(pathPrefix);
        Game.Node current = node;
        while (current != null) {
            List<Short> pathBeforeMove = List.copyOf(path);
            Position before = cursor.copy();
            short move = SAN.fromAlgebraic(cursor, current.getSan());
            Position next = cursor.copy();
            next.play(move);
            path.add(move);
            String indent = variationIndent(depth);
            displayRows.add(new MoveRow(indent + plyLabel(before), indent + PositionText.safeSan(before, move),
                    Move.toString(move), next.toString(), move, next.copy(), List.copyOf(path),
                    mainline, mainline ? 0 : depth));
            for (Game.Node variation : current.getVariations()) {
                appendSequenceRows(before, variation, depth + 1, pathBeforeMove, false);
            }
            cursor = next;
            current = current.getNext();
        }
    }

    /**
     * Rebuilds visible rows from the editable mainline.
     */
    private void rebuildMainlineDisplayRows() {
        displayRows.clear();
        mainlineRows.clear();
        mainlineRows.add(-1);
        for (MoveRow row : moves) {
            mainlineRows.add(displayRows.size());
            displayRows.add(row);
        }
    }

    /**
     * Returns the display-row index for a mainline ply.
     *
     * @param ply mainline ply
     * @return display-row index, or {@code -1} at the root
     */
    private int rowForMainlinePly(int ply) {
        if (ply <= 0 || ply >= mainlineRows.size()) {
            return -1;
        }
        return mainlineRows.get(ply);
    }

    /**
     * Returns the selected editable mainline path.
     *
     * @return path moves
     */
    private List<Short> currentMainlinePath() {
        List<Short> path = new ArrayList<>();
        for (int i = 0; i < currentPly && i < moves.size(); i++) {
            path.add(moves.get(i).move());
        }
        return path;
    }

    /**
     * Returns a path copy with one move appended.
     *
     * @param path existing path
     * @param move move to append
     * @return appended path
     */
    private static List<Short> pathWithMove(List<Short> path, short move) {
        List<Short> next = new ArrayList<>(path);
        next.add(move);
        return List.copyOf(next);
    }

    /**
     * Converts an encoded move path to immutable UCI text.
     *
     * @param path encoded move path
     * @return UCI move path
     */
    private static List<String> uciPath(List<Short> path) {
        List<String> text = new ArrayList<>();
        for (Short move : path) {
            if (move != null) {
                text.add(Move.toString(move.shortValue()));
            }
        }
        return List.copyOf(text);
    }

    /**
     * Returns indentation for a variation depth.
     *
     * @param depth variation depth
     * @return indentation prefix
     */
    private static String variationIndent(int depth) {
        return "  ".repeat(Math.max(1, depth));
    }

    /**
     * Builds a ply label from the position before a move.
     *
     * @param position position before move
     * @return move-number label
     */
    private static String plyLabel(Position position) {
        return position.fullMoveNumber() + (position.isWhiteToMove() ? "." : "...");
    }

    /**
     * Appends a SAN move-number prefix.
     *
     * @param sb target builder
     * @param position position before move
     */
    private static void appendMovePrefix(StringBuilder sb, Position position) {
        // sanLine() already appends a trailing space after every move, so this only
        // needs to add the move-number prefix (for White, or the first move of the
        // line) — adding another leading space here doubled every separator.
        if (sb.isEmpty() || position.isWhiteToMove()) {
            sb.append(plyLabel(position)).append(' ');
        }
    }

    /**
     * Immutable move-history row.
     *
     * @param ply ply label
     * @param san SAN move
     * @param uci UCI move
     * @param fen resulting FEN
     * @param move encoded move
     * @param position resulting position
     * @param path path from the root to this row
     * @param mainline true when row belongs to the mainline
     * @param variationDepth PGN variation nesting depth, or zero for mainline
     */
    private record MoveRow(String ply, String san, String uci, String fen, short move,
            Position position, List<Short> path, boolean mainline, int variationDepth) {
    }

    /**
     * Immutable mainline-ply snapshot for review and authoring panels.
     *
     * @param ply one-based ply number
     * @param san SAN move text
     * @param uci UCI move text
     * @param beforeFen position before the move
     * @param afterFen position after the move
     * @param move encoded move
     * @param whiteMove true when White made the move
     */
    public record PlySnapshot(
            int ply,
            String san,
            String uci,
            String beforeFen,
            String afterFen,
            short move,
            boolean whiteMove) {
    }

    /**
     * Immutable visible move-row snapshot for history and variation-tree views.
     *
     * @param rowIndex zero-based visible table row index
     * @param ply move-number label
     * @param san SAN move text
     * @param uci UCI move text
     * @param afterFen position after the move
     * @param pathPly move-path length from the root
     * @param mainline true when this row belongs to the editable mainline
     * @param variationDepth PGN variation nesting depth, or zero for mainline
     * @param uciPath UCI move path from the root to this row
     */
    public record VisibleMoveSnapshot(
            int rowIndex,
            String ply,
            String san,
            String uci,
            String afterFen,
            int pathPly,
            boolean mainline,
            int variationDepth,
            List<String> uciPath) {
    }
}
