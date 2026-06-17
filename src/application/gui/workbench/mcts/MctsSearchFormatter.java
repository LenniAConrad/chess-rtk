package application.gui.workbench.mcts;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;

/**
 * Formatting helpers for MCTS search snapshots.
 */
final class MctsSearchFormatter {

    private MctsSearchFormatter() {
    }

    /**
     * Returns the stable UCI-line id for a move line.
     *
     * @param line move line
     * @return stable node id
     */
    static String nodeId(short[] line) {
        return line == null || line.length == 0 ? "root" : uciText(line);
    }

    /**
     * Formats a move line as UCI tokens.
     *
     * @param line move line
     * @return UCI text
     */
    static String uciText(short[] line) {
        if (line == null || line.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (short move : line) {
            if (move == Move.NO_MOVE) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Move.toString(move));
        }
        return sb.toString();
    }

    /**
     * Formats a move in SAN, falling back to UCI if SAN cannot be produced.
     *
     * @param position position before the move
     * @param move move to format
     * @return SAN or UCI text
     */
    static String moveSan(Position position, short move) {
        if (move == Move.NO_MOVE) {
            return "";
        }
        try {
            return SAN.toAlgebraic(position, move);
        } catch (RuntimeException ex) {
            return Move.toString(move);
        }
    }

    /**
     * Formats a PV as SAN tokens.
     *
     * @param root root position
     * @param pv move sequence
     * @return SAN principal variation text
     */
    static String pvText(Position root, short[] pv) {
        Position cursor = root.copy();
        StringBuilder sb = new StringBuilder();
        for (short move : pv) {
            if (move == Move.NO_MOVE) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(moveSan(cursor, move));
            try {
                cursor.play(move);
            } catch (RuntimeException ex) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Converts a normalized value back to a compact centipawn display.
     *
     * @param value normalized value
     * @return centipawn approximation
     */
    static int valueToCentipawns(double value) {
        double v = Math.max(-0.999, Math.min(0.999, value));
        return (int) Math.round(600.0 * 0.5 * Math.log((1.0 + v) / (1.0 - v)));
    }
}
