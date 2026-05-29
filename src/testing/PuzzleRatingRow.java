package testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Mutable puzzle-rating CSV row with cached score and FEN keys.
 */
final class PuzzleRatingRow {

    /**
     * Original index.
     */
    private final int index;

    /**
     * Mutable CSV fields.
     */
    private final List<String> fields;

    /**
     * Raw difficulty score.
     */
    private final double rawScore;

    /**
     * FEN tie-break key.
     */
    private final String fen;

    /**
     * Creates a row.
     * @param index index value
     * @param fields record fields
     * @param rawScore raw score value
     * @param fen FEN string
     */
    PuzzleRatingRow(int index, List<String> fields, double rawScore, String fen) {
        this.index = index;
        this.fields = new ArrayList<>(fields);
        this.rawScore = rawScore;
        this.fen = fen;
    }

    /**
     * Original index.
     * @return index result
     */
    int index() {
        return index;
    }

    /**
     * CSV fields.
     * @return fields result
     */
    List<String> fields() {
        return fields;
    }

    /**
     * Raw score.
     * @return raw score result
     */
    double rawScore() {
        return rawScore;
    }

    /**
     * FEN key.
     * @return fen result
     */
    String fen() {
        return fen;
    }

    /**
     * Current rating.
     * @return rating result
     */
    int rating() {
        return Integer.parseInt(fields.get(2));
    }

    /**
     * Goal label.
     * @return goal result
     */
    String goal() {
        return fields.get(1);
    }

    /**
     * Difficulty label.
     * @return label result
     */
    String label() {
        return fields.get(5);
    }

    /**
     * Principal solution move or line.
     * @return solution result
     */
    String solution() {
        return fields.get(6);
    }

    /**
     * Cheap rank.
     * @return cheap rank result
     */
    int cheapRank() {
        return Integer.parseInt(fields.get(8));
    }

    /**
     * Legal move count.
     * @return legal moves result
     */
    int legalMoves() {
        return Integer.parseInt(fields.get(15));
    }

    /**
     * Explicit plies.
     * @return plies result
     */
    int plies() {
        return Integer.parseInt(fields.get(16));
    }

    /**
     * Root replies.
     * @return replies result
     */
    int replies() {
        return Integer.parseInt(fields.get(17));
    }

    /**
     * Tree nodes.
     * @return nodes result
     */
    int nodes() {
        return Integer.parseInt(fields.get(18));
    }

    /**
     * Branch point count.
     * @return branches result
     */
    int branches() {
        return Integer.parseInt(fields.get(19));
    }

    /**
     * Raw feature list.
     * @return features result
     */
    String features() {
        return fields.get(20);
    }

    /**
     * Whether this row contains a feature.
     * @param feature feature value
     * @return true when has feature
     */
    boolean hasFeature(String feature) {
        return featureSet().containsKey(feature);
    }

    /**
     * Feature set.
     * @return feature set result
     */
    Map<String, Boolean> featureSet() {
        Map<String, Boolean> out = new HashMap<>();
        for (String feature : fields.get(20).split("[,|]")) {
            String trimmed = feature.trim();
            if (!trimmed.isEmpty()) {
                out.put(trimmed, Boolean.TRUE);
            }
        }
        return out;
    }

}
