package application.gui.workbench.game;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Curated set of canned positions used by the network-panel position picker.
 *
 * <p>The set covers a spread of pawn structures, material, and tactical
 * situations so the user can flip between them and watch how each network's
 * activations shift.</p>
 */
public final class Positions {

    /**
     * Special key meaning "use whatever the main board is showing".
     */
    public static final String USE_MAIN_BOARD = "Use main board";

    /**
     * Ordered map of human label to FEN. LinkedHashMap preserves UI order.
     */
    private static final Map<String, String> namedPositions;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Start position",
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        map.put("Italian opening (3.Bc4)",
                "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3");
        map.put("French middlegame",
                "r1bq1rk1/pp1nbppp/2p1pn2/3pp3/2PP4/2N1PN2/PP3PPP/R1BQKB1R w KQ - 0 7");
        map.put("Sicilian tactical",
                "r1bqkb1r/pp2pppp/2np1n2/2pP4/4P3/2N5/PPP1BPPP/R1BQK1NR b KQkq - 0 5");
        map.put("Knight outpost",
                "r1bq1rk1/pp2bppp/2n1pn2/3p4/3P4/2NBPN2/PP3PPP/R1BQK2R w KQ - 0 8");
        map.put("Rook endgame",
                "8/5pkp/8/8/6P1/4R2K/5P2/8 w - - 0 1");
        map.put("K+P endgame",
                "8/8/4k3/8/4K3/4P3/8/8 w - - 0 1");
        map.put("Mate in one",
                "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4");
        map.put("Lennart's puzzle",
                "8/p1p3Q1/1p4r1/5qk1/5pp1/P7/1P5R/K7 w - - 0 1");
        namedPositions = Collections.unmodifiableMap(map);
    }

    /**
     * Returns the picker labels, with "use main board" pinned to the front.
     *
     * @return label array
     */
    public static String[] labels() {
        String[] keys = namedPositions.keySet().toArray(new String[0]);
        String[] out = new String[keys.length + 1];
        out[0] = USE_MAIN_BOARD;
        System.arraycopy(keys, 0, out, 1, keys.length);
        return out;
    }

    /**
     * Returns the FEN for a label, or null when the label is "use main board"
     * or unknown.
     *
     * @param label picker label
     * @return FEN or null
     */
    public static String fenFor(String label) {
        if (label == null || USE_MAIN_BOARD.equals(label)) {
            return null;
        }
        return namedPositions.get(label);
    }

    /**
     * Utility class; prevents instantiation.
     */
    private Positions() {
    }
}
