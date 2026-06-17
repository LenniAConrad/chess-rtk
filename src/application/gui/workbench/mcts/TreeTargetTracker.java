package application.gui.workbench.mcts;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.tag.game.SanResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses and tracks a target move line for the search-tree panel.
 */
final class TreeTargetTracker {
    /**
     * Parsed target line as encoded moves from {@link #rootFen}.
     */
    private short[] moves = new short[0];

    /**
     * Root FEN the target line was parsed against.
     */
    private String rootFen;

    /**
     * Elapsed millis when the target first became the root best move.
     */
    private long foundMillis = -1L;

    /**
     * Visit count when the target first became the root best move.
     */
    private long foundVisits;

    void parse(String nextRootFen, String text) {
        rootFen = nextRootFen;
        moves = parseLine(nextRootFen, text);
    }

    void resetTracking() {
        foundMillis = -1L;
        foundVisits = 0L;
    }

    short[] moves() {
        return moves;
    }

    boolean hasMoves() {
        return moves.length > 0;
    }

    String updateStatus(MctsSession.Snapshot snapshot, String text) {
        if (snapshot != null && !Objects.equals(snapshot.rootFen(), rootFen)) {
            parse(snapshot.rootFen(), text);
            resetTracking();
        }
        if (moves.length == 0) {
            return "no target";
        }
        MctsSearch.Snapshot root = snapshot == null ? null : snapshot.root();
        if (root == null) {
            return "set · start search";
        }
        boolean bestMatch = root.bestMove() == moves[0];
        int agree = commonPrefix(root.bestPv(), moves);
        if (bestMatch && foundMillis < 0) {
            foundMillis = root.elapsedMillis();
            foundVisits = root.playouts();
        }
        String pv = "PV " + agree + "/" + moves.length;
        if (foundMillis >= 0) {
            return String.format("found %.2fs · %,d v · %s",
                    foundMillis / 1000.0, foundVisits, pv);
        }
        return "searching · " + pv;
    }

    private static short[] parseLine(String rootFen, String text) {
        if (rootFen == null || rootFen.isBlank() || text == null || text.isBlank()) {
            return new short[0];
        }
        Position pos;
        try {
            pos = new Position(rootFen);
        } catch (RuntimeException ex) {
            return new short[0];
        }
        List<Short> parsed = new ArrayList<>();
        for (String raw : text.trim().split("\\s+")) {
            String token = raw.replaceAll("^\\d+\\.+", "").trim();
            if (token.isEmpty() || ".".equals(token)) {
                continue;
            }
            short move = SanResolver.resolve(pos, token);
            if (move == Move.NO_MOVE) {
                move = parseUci(pos, token);
            }
            if (move == Move.NO_MOVE) {
                break;
            }
            parsed.add(move);
            pos = pos.play(move);
        }
        short[] out = new short[parsed.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = parsed.get(i);
        }
        return out;
    }

    private static short parseUci(Position pos, String token) {
        if (!Move.isMove(token)) {
            return Move.NO_MOVE;
        }
        MoveList legal = pos.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.get(i);
            if (token.equalsIgnoreCase(Move.toString(move))) {
                return move;
            }
        }
        return Move.NO_MOVE;
    }

    private static int commonPrefix(short[] a, short[] b) {
        if (a == null) {
            return 0;
        }
        int n = Math.min(a.length, b.length);
        int i = 0;
        while (i < n && a[i] == b[i]) {
            i++;
        }
        return i;
    }
}
