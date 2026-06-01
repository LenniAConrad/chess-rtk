package chess.tag.game;

import java.util.ArrayList;
import java.util.List;

import chess.core.Move;
import chess.core.MoveInference;
import chess.core.Position;
import chess.struct.Game;
import chess.tag.Generator;
import chess.tag.MoveEffect;
import utility.Json;

/**
 * Layer 4: whole-game analysis. Replays a game's mainline from a start position
 * and assembles a grounded, structured annotation: per-move transition tags
 * ({@link MoveEffect}), the line-level multi-move tactics over the whole game
 * ({@link LineAnalyzer}), and {@code GAME:} summary tags (opening, phase
 * transitions, result cause).
 *
 * <p>The game is supplied as {@code (Position start, short[] moves)} — the same
 * shape {@link LineAnalyzer} consumes — keeping this layer independent of any
 * particular PGN/move-tree model; converting a PGN to that pair is a separate
 * concern.</p>
 *
 * <p>Engine-free and fully grounded: every datum is observed by replaying the
 * actual moves and tagging the actual positions. The result is the structured
 * input a downstream explanation/T5 layer turns into prose without guessing any
 * chess fact.</p>
 *
 * <p>Use {@link #analyze(Position, short[])} for the structured result and
 * {@link Analysis#toJson()} for nested JSON (game -&gt; moves -&gt; effects, plus
 * game-level lines and summary tags).</p>
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class GameAnalyzer {

    private GameAnalyzer() {
        // utility
    }

    /**
     * Analyzes a game's mainline.
     *
     * @param start the position the game starts from
     * @param moves the mainline moves, in order
     * @return the structured analysis (never {@code null}; empty when inputs are
     *     {@code null} or there are no moves)
     */
    /**
     * Analyzes a whole {@link Game}, mainline and sidelines together.
     *
     * <p>Resolves the game's SAN mainline to moves (via {@link SanResolver}) and
     * runs the per-ply / line / summary analysis over it, then attaches the
     * {@link VariationAnalyzer} result for every sideline. This is the single
     * entry point for "give me a parsed game, get its full grounded analysis".</p>
     *
     * @param game the game to analyze
     * @return the structured analysis (never {@code null}; empty when {@code game}
     *     or its start position is {@code null})
     */
    public static Analysis analyze(Game game) {
        if (game == null || game.getStartPosition() == null) {
            return new Analysis();
        }
        Position start = game.getStartPosition();
        List<String> mainlineSans = new ArrayList<>();
        for (Game.Node node = game.getMainline(); node != null; node = node.getNext()) {
            mainlineSans.add(node.getSan());
        }
        short[] moves = SanResolver.resolveLine(start, mainlineSans);
        Analysis analysis = analyze(start, moves);
        analysis.variations = VariationAnalyzer.analyze(game);
        return analysis;
    }

    public static Analysis analyze(Position start, short[] moves) {
        Analysis analysis = new Analysis();
        if (start == null || moves == null || moves.length == 0) {
            return analysis;
        }

        Position cur = start.copy();
        String prevPhase = phaseOf(Generator.tags(cur));
        int played = 0;
        for (int i = 0; i < moves.length; i++) {
            short mv = moves[i];
            if (!isLegal(cur, mv)) {
                break;
            }
            Position next = cur.copy().play(mv);
            String san = sanOf(cur, next, mv);

            MovePly ply = new MovePly();
            ply.ply = i;
            ply.san = san;
            ply.effects = MoveEffect.effects(cur, next);
            analysis.moves.add(ply);

            String phase = phaseOf(Generator.tags(next));
            if (phase != null && prevPhase != null && !phase.equals(prevPhase)) {
                analysis.gameTags.add("GAME: phase_transition=" + prevPhase + "->" + phase
                        + " ply=" + i + " move=" + san);
                prevPhase = phase;
            }
            cur = next;
            played++;
        }

        addOpening(analysis, start);
        addResultCause(analysis, cur);
        // LineAnalyzer over only the legally-played prefix.
        short[] legal = moves;
        if (played != moves.length) {
            legal = new short[played];
            System.arraycopy(moves, 0, legal, 0, played);
        }
        analysis.lines = LineAnalyzer.tags(start, legal);
        return analysis;
    }

    /**
     * Adds {@code GAME:} opening tags from the static opening/ECO tags of the
     * start position, when the ECO book matches.
     *
     * @param analysis the analysis being assembled
     * @param start the game's start position
     */
    private static void addOpening(Analysis analysis, Position start) {
        for (String tag : Generator.tags(start)) {
            if (tag.startsWith("OPENING: ") || tag.startsWith("META: eco=")
                    || tag.startsWith("META: opening=")) {
                analysis.gameTags.add("GAME: " + tag.substring(tag.indexOf(' ') + 1));
            }
        }
    }

    /**
     * Adds a {@code GAME: result_cause=} tag grounded in the terminal position.
     *
     * @param analysis the analysis being assembled
     * @param endPosition the position after the last played move
     */
    private static void addResultCause(Analysis analysis, Position endPosition) {
        if (endPosition.isCheckmate()) {
            String pattern = checkmatePattern(Generator.tags(endPosition));
            analysis.gameTags.add("GAME: result_cause=checkmate"
                    + (pattern == null ? "" : " pattern=" + pattern));
        } else if (endPosition.legalMoves().isEmpty()) {
            analysis.gameTags.add("GAME: result_cause=stalemate");
        }
    }

    /**
     * Returns the {@code CHECKMATE: pattern=} value from a tag list, or
     * {@code null} when none is present.
     *
     * @param tags the position's tags
     * @return the named checkmate pattern, or {@code null}
     */
    private static String checkmatePattern(List<String> tags) {
        for (String tag : tags) {
            if (tag.startsWith("CHECKMATE: pattern=")) {
                return tag.substring("CHECKMATE: pattern=".length());
            }
        }
        return null;
    }

    /**
     * Extracts the {@code META: phase=} value from a tag list.
     *
     * @param tags the position's tags
     * @return {@code opening}/{@code middlegame}/{@code endgame}, or {@code null}
     */
    private static String phaseOf(List<String> tags) {
        for (String tag : tags) {
            if (tag.startsWith("META: phase=")) {
                return tag.substring("META: phase=".length());
            }
        }
        return null;
    }

    /**
     * Returns the SAN for a move, falling back to UCI when SAN cannot be formed.
     *
     * @param parent the position before the move
     * @param child the position after the move
     * @param move the played move
     * @return the move in SAN, or its UCI form
     */
    private static String sanOf(Position parent, Position child, short move) {
        MoveInference.Notation notation = MoveInference.notation(parent, child);
        if (notation != null && notation.san() != null && !notation.san().isBlank()) {
            return notation.san();
        }
        return Move.toString(move);
    }

    /**
     * Returns whether a move is legal in a position.
     *
     * @param position the position to test against
     * @param move the candidate move
     * @return {@code true} when {@code move} is in the position's legal move list
     */
    private static boolean isLegal(Position position, short move) {
        var legal = position.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            if (legal.get(i) == move) {
                return true;
            }
        }
        return false;
    }

    /**
     * One mainline ply with its move-transition effects.
     */
    public static final class MovePly {
        /**
         * Zero-based ply index.
         */
        public int ply;
        /**
         * The move in SAN.
         */
        public String san;
        /**
         * The {@code MOVE_EFFECT:} tags for this ply.
         */
        public List<String> effects = new ArrayList<>();
    }

    /**
     * Structured whole-game analysis: per-ply effects, game-level line tactics,
     * and {@code GAME:} summary tags.
     */
    public static final class Analysis {
        /**
         * Per-ply move-transition tags, in game order.
         */
        public final List<MovePly> moves = new ArrayList<>();
        /**
         * Game-level {@code LINE:} tactic tags over the whole mainline.
         */
        public List<String> lines = new ArrayList<>();
        /**
         * Game-level {@code GAME:} summary tags.
         */
        public final List<String> gameTags = new ArrayList<>();
        /**
         * Sideline analysis ({@link VariationAnalyzer}); empty unless the game
         * was analyzed via {@link #analyze(Game)}.
         */
        public VariationAnalyzer.Result variations = new VariationAnalyzer.Result();

        /**
         * Serializes the analysis to nested JSON: {@code {gameTags, lines,
         * variations, moves:[{ply, san, effects}]}}.
         *
         * @return the JSON representation
         */
        public String toJson() {
            StringBuilder sb = new StringBuilder(256);
            sb.append('{');
            sb.append("\"gameTags\":").append(Json.stringArray(gameTags.toArray(new String[0])));
            sb.append(",\"lines\":").append(Json.stringArray(lines.toArray(new String[0])));
            sb.append(",\"variations\":").append(variations.arrayJson());
            sb.append(",\"sharedTactics\":")
                    .append(Json.stringArray(variations.sharedTactics.toArray(new String[0])));
            sb.append(",\"moves\":[");
            for (int i = 0; i < moves.size(); i++) {
                MovePly ply = moves.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"ply\":").append(ply.ply)
                        .append(",\"san\":\"").append(Json.esc(ply.san)).append('"')
                        .append(",\"effects\":").append(Json.stringArray(ply.effects.toArray(new String[0])))
                        .append('}');
            }
            sb.append("]}");
            return sb.toString();
        }
    }
}
