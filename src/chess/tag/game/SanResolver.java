package chess.tag.game;

import java.util.ArrayList;
import java.util.List;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;

/**
 * Resolves SAN move text back to concrete encoded moves against a position, so
 * the SAN-only {@link chess.struct.Game} move tree can be replayed.
 *
 * <p>There is no SAN parser in the move core ({@link SAN} only renders move to
 * SAN), so resolution works by generating the legal moves at the position and
 * matching their rendered SAN, tolerating trailing check/mate and annotation
 * glyphs. This keeps replay fully grounded: a SAN resolves only to a move that
 * is actually legal in the position.</p>
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class SanResolver {

    /**
     * Creates the SAN resolver.
     */
    private SanResolver() {
        // utility
    }

    /**
     * Resolves a single SAN to a legal move in a position.
     *
     * @param position the position the move is played in
     * @param san the SAN to resolve
     * @return the encoded move, or {@link Move#NO_MOVE} when no legal move matches
     */
    public static short resolve(Position position, String san) {
        if (position == null || san == null || san.isBlank()) {
            return Move.NO_MOVE;
        }
        String target = san.trim();
        MoveList legal = position.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.get(i);
            if (target.equals(SAN.toAlgebraic(position, move))) {
                return move;
            }
        }
        String stripped = strip(target);
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.get(i);
            if (stripped.equals(strip(SAN.toAlgebraic(position, move)))) {
                return move;
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Resolves a sequence of SAN moves to encoded moves by replaying them from a
     * start position, stopping at the first move that cannot be legally played.
     *
     * @param start the position to replay from
     * @param sans the SAN moves, in order
     * @return the legally-resolvable prefix as encoded moves (possibly shorter
     *     than {@code sans} when a move fails to resolve)
     */
    public static short[] resolveLine(Position start, List<String> sans) {
        List<Short> moves = new ArrayList<>();
        if (start == null || sans == null) {
            return new short[0];
        }
        Position cur = start.copy();
        for (String san : sans) {
            short move = resolve(cur, san);
            if (move == Move.NO_MOVE) {
                break;
            }
            moves.add(move);
            cur = cur.copy().play(move);
        }
        short[] array = new short[moves.size()];
        for (int i = 0; i < moves.size(); i++) {
            array[i] = moves.get(i);
        }
        return array;
    }

    /**
     * Strips trailing check ({@code +}), mate ({@code #}) and annotation
     * ({@code !}/{@code ?}) glyphs from a SAN token.
     *
     * @param san the SAN token
     * @return the token without trailing decoration
     */
    private static String strip(String san) {
        int end = san.length();
        while (end > 0) {
            char c = san.charAt(end - 1);
            if (c == '+' || c == '#' || c == '!' || c == '?') {
                end--;
            } else {
                break;
            }
        }
        return san.substring(0, end);
    }
}
