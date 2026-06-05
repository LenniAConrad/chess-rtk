package chess.describe;

import java.io.Serializable;
import java.util.List;

import chess.classical.Wdl;
import chess.core.Position;
import utility.Json;

/**
 * Shared structured feature input consumed by position-text generators.
 *
 * <p>
 * The object is intentionally deterministic and serializable. It contains only
 * cheap/static signals unless a caller explicitly supplies extra data later.
 * </p>
 *
 * @param fen normalized FEN
 * @param sideToMove lowercase side-to-move label
 * @param status tactical game status label
 * @param inCheck true when the side to move is in check
 * @param phase coarse game phase
 * @param material material summary
 * @param moves legal-move summary
 * @param evaluation cheap static evaluation
 * @param tags deterministic source tags
 * @param threats immediate forcing signals
 * @param candidates candidate moves selected by deterministic scoring
 */
public record PositionDescriptionInput(
        String fen,
        String sideToMove,
        String status,
        boolean inCheck,
        String phase,
        Material material,
        MoveSummary moves,
        Evaluation evaluation,
        List<String> tags,
        List<String> threats,
        List<CandidateMove> candidates) implements Serializable {

    /**
     * Serialization identifier for stable record serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates an input from a position using cheap deterministic extraction.
     *
     * @param position position
     * @return extracted input
     */
    public static PositionDescriptionInput from(Position position) {
        return PositionDescriptionExtractor.extract(position);
    }

    /**
     * Creates an input from FEN using cheap deterministic extraction.
     *
     * @param fen FEN text
     * @return extracted input
     */
    public static PositionDescriptionInput fromFen(String fen) {
        return from(new Position(fen));
    }

    /**
     * Normalizes nullable list fields.
     */
    public PositionDescriptionInput {
        tags = List.copyOf(tags == null ? List.of() : tags);
        threats = List.copyOf(threats == null ? List.of() : threats);
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
    }

    /**
     * Returns a copy of this input with a replaced evaluation.
     *
     * <p>
     * Used to swap the cheap static evaluation for a real engine-search evaluation
     * without recomputing the rest of the structured signals.
     * </p>
     *
     * @param replacement evaluation to substitute
     * @return copy with the replacement evaluation
     */
    public PositionDescriptionInput withEvaluation(Evaluation replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException("replacement == null");
        }
        return new PositionDescriptionInput(fen, sideToMove, status, inCheck, phase, material, moves,
                replacement, tags, threats, candidates);
    }

    /**
     * Serializes this input to a compact JSON object.
     *
     * @return JSON object
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(768);
        sb.append('{')
                .append("\"fen\":").append(json(fen)).append(',')
                .append("\"side_to_move\":").append(json(sideToMove)).append(',')
                .append("\"status\":").append(json(status)).append(',')
                .append("\"in_check\":").append(inCheck).append(',')
                .append("\"phase\":").append(json(phase)).append(',')
                .append("\"material\":").append(material.toJson()).append(',')
                .append("\"moves\":").append(moves.toJson()).append(',')
                .append("\"evaluation\":").append(evaluation.toJson()).append(',')
                .append("\"tags\":").append(stringArray(tags)).append(',')
                .append("\"threats\":").append(stringArray(threats)).append(',')
                .append("\"candidates\":").append(candidateArray(candidates))
                .append('}');
        return sb.toString();
    }

    /**
     * Material summary from White's perspective.
     *
     * @param whiteCp White material centipawns
     * @param blackCp Black material centipawns
     * @param balanceCp White minus Black material centipawns
     * @param white White piece counts
     * @param black Black piece counts
     */
    public record Material(
            int whiteCp,
            int blackCp,
            int balanceCp,
            PieceCounts white,
            PieceCounts black) implements Serializable {

        /**
         * Serialization identifier for stable record serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Serializes this material summary.
         *
         * @return JSON object
         */
        public String toJson() {
            return "{\"white_cp\":" + whiteCp
                    + ",\"black_cp\":" + blackCp
                    + ",\"balance_cp\":" + balanceCp
                    + ",\"white\":" + white.toJson()
                    + ",\"black\":" + black.toJson()
                    + "}";
        }
    }

    /**
     * Per-side piece counts.
     *
     * @param kings kings
     * @param queens queens
     * @param rooks rooks
     * @param bishops bishops
     * @param knights knights
     * @param pawns pawns
     */
    public record PieceCounts(
            int kings,
            int queens,
            int rooks,
            int bishops,
            int knights,
            int pawns) implements Serializable {

        /**
         * Serialization identifier for stable record serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Formats major/minor/pawn counts compactly for text.
         *
         * @return compact material text
         */
        public String compact() {
            return "Q" + queens + " R" + rooks + " B" + bishops + " N" + knights + " P" + pawns;
        }

        /**
         * Serializes this piece-count summary.
         *
         * @return JSON object
         */
        public String toJson() {
            return "{\"kings\":" + kings
                    + ",\"queens\":" + queens
                    + ",\"rooks\":" + rooks
                    + ",\"bishops\":" + bishops
                    + ",\"knights\":" + knights
                    + ",\"pawns\":" + pawns
                    + "}";
        }
    }

    /**
     * Legal-move summary for the side to move.
     *
     * @param legal legal moves
     * @param captures captures
     * @param checks checking moves
     * @param mates mate-in-one moves
     * @param promotions promotions
     * @param castles castling moves
     * @param enPassant en-passant captures
     * @param quiet quiet moves
     * @param forcing captures, checks, or promotions
     */
    public record MoveSummary(
            int legal,
            int captures,
            int checks,
            int mates,
            int promotions,
            int castles,
            int enPassant,
            int quiet,
            int forcing) implements Serializable {

        /**
         * Serialization identifier for stable record serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Serializes this move summary.
         *
         * @return JSON object
         */
        public String toJson() {
            return "{\"legal\":" + legal
                    + ",\"captures\":" + captures
                    + ",\"checks\":" + checks
                    + ",\"mates\":" + mates
                    + ",\"promotions\":" + promotions
                    + ",\"castles\":" + castles
                    + ",\"en_passant\":" + enPassant
                    + ",\"quiet\":" + quiet
                    + ",\"forcing\":" + forcing
                    + "}";
        }
    }

    /**
     * Position evaluation signal, either cheap static or a real engine search.
     *
     * @param source evaluation source label (e.g. {@code classical-static} or
     *        {@code engine-d10})
     * @param cpWhite centipawns from White's perspective
     * @param cpSideToMove centipawns from the side-to-move perspective
     * @param wdl WDL from side-to-move perspective, or null when not modeled
     * @param mateIn forced-mate distance in moves from White's perspective: a
     *        positive value means White mates in that many moves, a negative value
     *        means Black mates, and zero means no forced mate is known
     */
    public record Evaluation(
            String source,
            int cpWhite,
            int cpSideToMove,
            Wdl wdl,
            int mateIn) implements Serializable {

        /**
         * Serialization identifier for stable record serialization.
         */
        private static final long serialVersionUID = 2L;

        /**
         * Returns whether this evaluation reports a forced mate.
         *
         * @return true when a forced mate is known
         */
        public boolean hasMate() {
            return mateIn != 0;
        }

        /**
         * Serializes this evaluation.
         *
         * @return JSON object
         */
        public String toJson() {
            return appendTo(new StringBuilder("{\"source\":"), source)
                    .append(",\"cp_white\":").append(cpWhite)
                    .append(",\"cp_side_to_move\":").append(cpSideToMove)
                    .append(",\"wdl\":").append(wdl == null ? "null" : wdlJson(wdl))
                    .append(",\"mate_in\":").append(mateIn)
                    .append('}')
                    .toString();
        }

        /**
         * Appends a JSON string value.
         *
         * @param sb target
         * @param value value
         * @return target
         */
        private static StringBuilder appendTo(StringBuilder sb, String value) {
            return sb.append(json(value));
        }
    }

    /**
     * Candidate move selected by cheap deterministic move scoring.
     *
     * @param uci UCI move
     * @param san SAN move
     * @param reason short reason label
     * @param priority deterministic priority score
     */
    public record CandidateMove(
            String uci,
            String san,
            String reason,
            int priority) implements Serializable {

        /**
         * Serialization identifier for stable record serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Serializes this candidate.
         *
         * @return JSON object
         */
        public String toJson() {
            return "{\"uci\":" + json(uci)
                    + ",\"san\":" + json(san)
                    + ",\"reason\":" + json(reason)
                    + ",\"priority\":" + priority
                    + "}";
        }
    }

    /**
     * Serializes candidate moves as a JSON array.
     *
     * @param values candidate moves
     * @return JSON array
     */
    private static String candidateArray(List<CandidateMove> values) {
        StringBuilder sb = new StringBuilder(values.size() * 80 + 2).append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i).toJson());
        }
        return sb.append(']').toString();
    }

    /**
     * Serializes strings as a JSON array.
     *
     * @param values string values
     * @return JSON array
     */
    private static String stringArray(List<String> values) {
        StringBuilder sb = new StringBuilder(values.size() * 32 + 2).append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(json(values.get(i)));
        }
        return sb.append(']').toString();
    }

    /**
     * Serializes one nullable string as JSON.
     *
     * @param value nullable string
     * @return JSON string or {@code null}
     */
    private static String json(String value) {
        return value == null ? "null" : "\"" + Json.esc(value) + "\"";
    }

    /**
     * Serializes one WDL tuple as JSON.
     *
     * @param wdl WDL tuple
     * @return JSON object
     */
    private static String wdlJson(Wdl wdl) {
        return "{\"win\":" + wdl.win()
                + ",\"draw\":" + wdl.draw()
                + ",\"loss\":" + wdl.loss()
                + "}";
    }
}
