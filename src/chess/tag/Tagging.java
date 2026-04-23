package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import chess.classical.Wdl;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.eval.Evaluator;
import chess.eval.Result;
import chess.tag.core.Text;
import chess.tag.material.Endgame;
import chess.tag.material.Material;
import chess.tag.pawn.PawnStructure;
import chess.tag.pawn.Promotion;
import chess.tag.piece.PieceAblation;
import chess.tag.piece.PieceActivity;
import chess.tag.position.CenterSpace;
import chess.tag.position.Opening;
import chess.tag.tactical.Tactical;
import chess.uci.Analysis;
import chess.uci.Chances;
import chess.uci.Evaluation;
import chess.uci.Output;
import utility.Numbers;

/**
 * Builds the canonical tag set for a chess position.
 * <p>
 * The tagging pipeline combines engine information, board state, tactical
 * motifs, structural heuristics, and opening/endgame signals into a sorted
 * list of tag strings.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Tagging {

    /**
     * The total starting material used to estimate the game phase.
     */
    private static final int START_TOTAL_MATERIAL = 8000;

    /**
     * The material fraction above which the position is treated as opening-like.
     */
    private static final double PHASE_OPENING = 0.75;

    /**
     * The material fraction above which the position is treated as middlegame-like.
     */
    private static final double PHASE_MIDDLEGAME = 0.35;

    /**
     * Prevents instantiation of this utility class.
     */
    private Tagging() {
        // utility
    }

    /**
     * Provides a lazily created shared evaluator for callers that do not supply one.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class SharedEvaluator {

        /**
         * The shared evaluator instance.
         */
        private static final AtomicReference<Evaluator> INSTANCE = new AtomicReference<>();

        /**
         * Tracks whether the JVM shutdown hook has already been installed.
         */
        private static final AtomicBoolean SHUTDOWN_HOOK_INSTALLED = new AtomicBoolean(false);

        /**
         * Prevents instantiation of this helper class.
         */
        private SharedEvaluator() {
            // utility
        }

        /**
         * Returns the shared evaluator, creating it on demand.
         *
         * @return the process-wide shared evaluator
         */
        static Evaluator get() {
            Evaluator existing = INSTANCE.get();
            if (existing != null) {
                return existing;
            }
            Evaluator created = new Evaluator();
            if (INSTANCE.compareAndSet(null, created)) {
                installShutdownHook();
                return created;
            }
            try {
                created.close();
            } catch (Exception ignore) {
                // best-effort cleanup
            }
            return INSTANCE.get();
        }

        /**
         * Installs a shutdown hook that closes the shared evaluator on exit.
         */
        private static void installShutdownHook() {
            if (!SHUTDOWN_HOOK_INSTALLED.compareAndSet(false, true)) {
                return;
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Evaluator shared = INSTANCE.get();
                if (shared != null) {
                    try {
                        shared.close();
                    } catch (Exception ignore) {
                        // best-effort cleanup
                    }
                }
            }, CRTK_TAG_EVALUATOR_SHUTDOWN));
        }
    }

    /**
     * Derives tags for a position using the shared evaluator and no analysis.
     *
     * @param position the position to tag
     * @return the canonical, sorted tag list
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, POSITION);
        return tags(position, SharedEvaluator.get(), null);
    }

    /**
     * Derives tags for a position using analysis data when available.
     *
     * @param position the position to tag
     * @param analysis optional engine analysis for PV and evaluation metadata
     * @return the canonical, sorted tag list
     */
    public static List<String> tags(Position position, Analysis analysis) {
        Objects.requireNonNull(position, POSITION);
        return tags(position, SharedEvaluator.get(), analysis);
    }

    /**
     * Derives tags for a position using the supplied evaluator and no analysis.
     *
     * @param position the position to tag
     * @param evaluator the evaluator used for static evaluation and WDL data
     * @return the canonical, sorted tag list
     */
    public static List<String> tags(Position position, Evaluator evaluator) {
        Objects.requireNonNull(position, POSITION);
        Objects.requireNonNull(evaluator, EVALUATOR);
        return tags(position, evaluator, null);
    }

    /**
     * Derives the full canonical tag set for a position.
     * <p>
     * This is the primary tagging entry point. It combines all tag categories,
     * then sorts and deduplicates the result before returning it.
     * </p>
     *
     * @param position the position to tag
     * @param evaluator the evaluator used for evaluation-derived metadata
     * @param analysis optional engine analysis, if available
     * @return the canonical, sorted, deduplicated tag list
     */
    public static List<String> tags(Position position, Evaluator evaluator, Analysis analysis) {
        Objects.requireNonNull(position, POSITION);
        Objects.requireNonNull(evaluator, EVALUATOR);

        TagContext ctx = new TagContext(position);
        List<String> tags = new ArrayList<>(128);

        addMeta(tags, ctx, position, evaluator, analysis);
        addCandidatesAndPv(tags, position, analysis);
        addFacts(tags, ctx, position);
        addMaterial(tags, position);
        addPieceTags(tags, position, evaluator);
        addPawnTags(tags, ctx, position);
        addKingTags(tags, position);
        addTacticalTags(tags, position);
        addStrategicTags(tags, ctx, position);
        addEndgameOpening(tags, position);

        return Sort.sort(tags);
    }

    /**
     * Returns the canonical tags as an array for positional output code.
     *
     * @param position the position to tag
     * @return the canonical tag list as an array
     */
    public static String[] positionalTags(Position position) {
        List<String> tags = tags(position);
        return tags.toArray(new String[0]);
    }

    /**
     * Adds meta tags such as side to move, phase, evaluation, WDL, and source.
     *
     * @param tags the mutable tag accumulator
     * @param ctx the shared tagging context
     * @param position the position being tagged
     * @param evaluator the evaluator used for fallback evaluation
     * @param analysis optional engine analysis
     */
    private static void addMeta(List<String> tags, TagContext ctx, Position position, Evaluator evaluator,
            Analysis analysis) {
        tags.add(META_TO_MOVE_PREFIX + (position.isWhiteToMove() ? WHITE : BLACK));
        tags.add(META_PHASE_PREFIX + phaseLabel(position));
        addMetaEvaluation(tags, ctx, position, evaluator, analysis);
        addMetaWdl(tags, ctx, position, evaluator, analysis);
        if (ctx.wdl != null) {
            tags.add(META_DIFFICULTY_PREFIX + difficultyLabel(ctx.wdl));
        }
        tags.add(META_SOURCE_PREFIX + (analysis != null ? ANALYSIS : ENGINE));
    }

    /**
     * Adds candidate-move and principal-variation tags from engine analysis.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     * @param analysis optional engine analysis
     */
    private static void addCandidatesAndPv(List<String> tags, Position position, Analysis analysis) {
        if (analysis == null || analysis.isEmpty()) {
            return;
        }
        addCandidate(tags, position, analysis, 1, BEST);
        if (analysis.getPivots() >= 2) {
            addCandidate(tags, position, analysis, 2, ALT);
        }
        addPv(tags, position, analysis);
    }

    /**
     * Adds a single candidate move tag from analysis data.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     * @param analysis the engine analysis containing candidate data
     * @param pv the principal-variation index to query
     * @param role the role label for the candidate, such as best or alternate
     */
    private static void addCandidate(List<String> tags, Position position, Analysis analysis, int pv, String role) {
        Output output = analysis.getBestOutput(pv);
        if (output == null) {
            return;
        }
        short move = analysis.getBestMove(pv);
        if (move == Move.NO_MOVE) {
            return;
        }
        String san;
        try {
            san = SAN.toAlgebraic(position, move);
        } catch (RuntimeException ex) {
            san = Move.toString(move);
        }
        Integer evalCp = evalFromOutput(output);
        StringBuilder sb = new StringBuilder(96);
        sb.append(CAND_PREFIX).append(role).append(CAND_MOVE_FIELD).append(san);
        if (evalCp != null) {
            sb.append(CAND_EVAL_CP_FIELD).append(evalCp);
        }
        sb.append(CAND_EMPTY_NOTE_FIELD);
        tags.add(sb.toString());
    }

    /**
     * Adds a principal variation tag from analysis data.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     * @param analysis the engine analysis containing the PV
     */
    private static void addPv(List<String> tags, Position position, Analysis analysis) {
        Output output = analysis.getBestOutput();
        if (output == null) {
            return;
        }
        short[] moves = output.getMoves();
        if (moves == null || moves.length == 0) {
            return;
        }
        int limit = Math.min(6, moves.length);
        List<String> sanMoves = new ArrayList<>(limit);
        Position cursor = position.copy();
        for (int i = 0; i < limit; i++) {
            short move = moves[i];
            if (move == Move.NO_MOVE) {
                break;
            }
            String san;
            try {
                san = SAN.toAlgebraic(cursor, move);
            } catch (RuntimeException ex) {
                san = Move.toString(move);
            }
            sanMoves.add(san);
            cursor.play(move);
        }
        if (sanMoves.isEmpty()) {
            return;
        }
        tags.add(PV_PREFIX + String.join(SPACE_TEXT, sanMoves));
    }

    /**
     * Adds an evaluation bucket tag based on the centipawn score.
     *
     * @param tags the mutable tag accumulator
     * @param evalCpWhite the evaluation from White's perspective, in centipawns
     */
    private static void addEvalBucket(List<String> tags, Integer evalCpWhite) {
        if (evalCpWhite == null) {
            return;
        }
        int cp = evalCpWhite;
        int abs = Math.abs(cp);
        if (abs < 80) {
            tags.add(META_EVAL_BUCKET_PREFIX + EQUAL);
            return;
        }
        String side = cp > 0 ? WHITE : BLACK;
        String bucket;
        if (abs < 250) {
            bucket = SLIGHT_PREFIX + side;
        } else if (abs < 700) {
            bucket = CLEAR_PREFIX + side;
        } else if (abs < 900) {
            bucket = WINNING_PREFIX + side;
        } else {
            bucket = CRUSHING_PREFIX + side;
        }
        tags.add(META_EVAL_BUCKET_PREFIX + bucket);
    }

    /**
     * Adds general factual tags such as status, castling rights, en passant, and center state.
     *
     * @param tags the mutable tag accumulator
     * @param ctx the shared tagging context
     * @param position the position being tagged
     */
    private static void addFacts(List<String> tags, TagContext ctx, Position position) {
        addStatusFacts(tags, position);
        addCastleFacts(tags, position);
        addEnPassantFact(tags, position);
        addCenterFacts(tags, ctx, position);
    }

    /**
     * Adds material balance and material-structure tags.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addMaterial(List<String> tags, Position position) {
        int discrepancy = position.materialDiscrepancy();
        tags.add(MATERIAL_BALANCE_PREFIX + balanceLabel(discrepancy));
        addMaterialImbalances(tags, position);
        addMaterialPieceCounts(tags, position);
    }

    /**
     * Adds the metadata-derived evaluation and WDL tags.
     *
     * @param tags the mutable tag accumulator
     * @param ctx the shared tagging context
     * @param position the position being tagged
     * @param evaluator the evaluator used for fallback evaluation
     * @param analysis optional engine analysis
     */
    private static void addMetaEvaluation(List<String> tags, TagContext ctx, Position position, Evaluator evaluator,
            Analysis analysis) {
        Evaluation analysisEval = evaluationFrom(analysis);
        if (analysisEval != null && analysisEval.isMate() && analysisEval.getValue() != 0) {
            addMateTag(tags, analysisEval.getValue());
            return;
        }
        Integer cp = evalCentipawns(evaluator, position, analysisEval);
        if (cp != null) {
            ctx.evalCpWhite = position.isWhiteToMove() ? cp : -cp;
            tags.add(META_EVAL_CP_PREFIX + cp);
            addEvalBucket(tags, ctx.evalCpWhite);
        }
    }

    /**
     * Adds a mate or mate-in tag using the supplied mate score.
     *
     * @param tags the mutable tag accumulator
     * @param mate the mate score, positive for winning mate and negative for being mated
     */
    private static void addMateTag(List<String> tags, int mate) {
        String key = mate > 0 ? META_MATE_IN_PREFIX : META_MATED_IN_PREFIX;
        tags.add(key + Math.abs(mate));
    }

    /**
     * Adds WDL metadata, preferring engine chances when available.
     *
     * @param tags the mutable tag accumulator
     * @param ctx the shared tagging context
     * @param position the position being tagged
     * @param evaluator the evaluator used for fallback evaluation
     * @param analysis optional engine analysis
     */
    private static void addMetaWdl(List<String> tags, TagContext ctx, Position position, Evaluator evaluator,
            Analysis analysis) {
        Chances chances = chancesFrom(analysis);
        if (chances != null) {
            tags.add(META_WDL_PREFIX + chances.getWinChance() + SLASH_SEPARATOR + chances.getDrawChance()
                    + SLASH_SEPARATOR + chances.getLossChance());
            ctx.wdl = new Wdl(chances.getWinChance(), chances.getDrawChance(), chances.getLossChance());
            return;
        }
        Result result = evaluator.evaluate(position);
        if (result != null) {
            Wdl wdl = result.wdl();
            if (wdl != null) {
                tags.add(META_WDL_PREFIX + wdl.win() + SLASH_SEPARATOR + wdl.draw() + SLASH_SEPARATOR + wdl.loss());
                ctx.wdl = wdl;
            }
        }
    }

    /**
     * Adds board status facts for check, checkmate, stalemate, or normal play.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addStatusFacts(List<String> tags, Position position) {
        String status = statusLabel(position);
        if (NORMAL.equals(status) && isInsufficientMaterial(position)) {
            status = INSUFFICIENT;
        }
        tags.add(STATUS_PREFIX + status);
        String inCheckSide = NONE;
        if (position.inCheck()) {
            inCheckSide = position.isWhiteToMove() ? WHITE : BLACK;
        }
        tags.add(FACT_IN_CHECK_PREFIX + inCheckSide);
    }

    /**
     * Adds castling-rights facts for the current position.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addCastleFacts(List<String> tags, Position position) {
        String castleRights = castleRightsLabel(position);
        if (castleRights != null) {
            tags.add(FACT_CASTLE_RIGHTS_PREFIX + castleRights);
        }
    }

    /**
     * Adds an en-passant fact when an en-passant square is available.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addEnPassantFact(List<String> tags, Position position) {
        byte enPassant = position.enPassantSquare();
        if (enPassant != Field.NO_SQUARE) {
            tags.add(FACT_EN_PASSANT_PREFIX + Text.squareNameLower(enPassant));
        }
    }

    /**
     * Adds center-control and space facts derived from the positional center analysis.
     *
     * @param tags the mutable tag accumulator
     * @param ctx the shared tagging context
     * @param position the position being tagged
     */
    private static void addCenterFacts(List<String> tags, TagContext ctx, Position position) {
        for (String tag : CenterSpace.tags(position)) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            applyCenterFact(tags, ctx, tag.trim());
        }
    }

    /**
     * Applies a single center-related fact to the output and context.
     *
     * @param tags the mutable tag accumulator
     * @param ctx the shared tagging context
     * @param trimmed the trimmed center-related tag text
     */
    private static void applyCenterFact(List<String> tags, TagContext ctx, String trimmed) {
        if (trimmed.startsWith(CENTER_CONTROL_PREFIX)) {
            String val = valueAfter(trimmed, CENTER_CONTROL_PREFIX);
            if (val != null) {
                ctx.centerControl = val;
                tags.add(CENTER_CONTROL_PREFIX + val);
            }
            return;
        }
        if (trimmed.startsWith(SPACE_ADVANTAGE_PREFIX)) {
            String val = valueAfter(trimmed, SPACE_ADVANTAGE_PREFIX);
            if (val != null) {
                ctx.spaceAdvantage = val;
                tags.add(SPACE_ADVANTAGE_PREFIX + val);
            }
            return;
        }
        if (trimmed.startsWith(FACT_CENTER_STATE_PREFIX)) {
            String val = valueAfter(trimmed, FACT_CENTER_STATE_PREFIX);
            if (val != null) {
                tags.add(FACT_CENTER_STATE_PREFIX + val);
            }
        }
    }

    /**
     * Adds material imbalance tags such as bishop pair, queenless, and rookless states.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addMaterialImbalances(List<String> tags, Position position) {
        int whiteBishops = position.countPieces(Piece.WHITE_BISHOP);
        int blackBishops = position.countPieces(Piece.BLACK_BISHOP);
        int whiteQueens = position.countPieces(Piece.WHITE_QUEEN);
        int blackQueens = position.countPieces(Piece.BLACK_QUEEN);
        int whiteRooks = position.countPieces(Piece.WHITE_ROOK);
        int blackRooks = position.countPieces(Piece.BLACK_ROOK);

        if (whiteBishops >= 2) {
            tags.add(MATERIAL_IMBALANCE_PREFIX + BISHOP_PAIR_WHITE);
        }
        if (blackBishops >= 2) {
            tags.add(MATERIAL_IMBALANCE_PREFIX + BISHOP_PAIR_BLACK);
        }
        if (whiteQueens + blackQueens == 0) {
            tags.add(MATERIAL_IMBALANCE_PREFIX + QUEENLESS);
        }
        if (whiteRooks + blackRooks == 0) {
            tags.add(MATERIAL_IMBALANCE_PREFIX + ROOKLESS);
        }
        if (whiteBishops > 0 && blackBishops > 0) {
            if (hasOppositeColoredBishops(position.getBoard())) {
                tags.add(MATERIAL_IMBALANCE_PREFIX + OPPOSITE_COLOR_BISHOPS);
            } else {
                tags.add(MATERIAL_IMBALANCE_PREFIX + SAME_COLOR_BISHOPS);
            }
        }
    }

    /**
     * Adds normalized material piece-count tags from the material subsystem.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addMaterialPieceCounts(List<String> tags, Position position) {
        for (String tag : Material.tags(position)) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.startsWith(FACT_PIECE_COUNT_PREFIX)) {
                Map<String, String> kv = parseKeyValues(trimmed.substring(FACT_HEADER.length()).trim());
                String side = kv.get(SIDE);
                String piece = kv.get(PIECE_KEY);
                String count = kv.get(COUNT);
                if (side != null && piece != null && count != null) {
                    tags.add(MATERIAL_PIECE_COUNT_PREFIX + side + SPACE_TEXT + PIECE_KEY + EQUAL_SIGN + piece
                            + COUNT_FIELD + count);
                }
            }
        }
    }

    /**
     * Adds a normalized piece-tier tag parsed from an ablation description.
     *
     * @param tags the mutable tag accumulator
     * @param trimmed the trimmed piece-related tag text
     */
    private static void addPieceTierTag(List<String> tags, String trimmed) {
        if (trimmed.contains(COLON_SPACE)) {
            addPieceExtremeTag(tags, trimmed);
            return;
        }
        ParsedPieceTier parsed = parseTierTag(trimmed);
        if (parsed != null) {
            tags.add(PIECE_TIER_PREFIX + parsed.tier + SIDE_FIELD + parsed.side + SPACE_TEXT + PIECE_KEY
                    + EQUAL_SIGN + parsed.piece + SQUARE_FIELD + parsed.square);
        }
    }

    /**
     * Adds a piece-extreme tag parsed from an ablation description.
     *
     * @param tags the mutable tag accumulator
     * @param trimmed the trimmed piece-related tag text
     */
    private static void addPieceExtremeTag(List<String> tags, String trimmed) {
        String[] parts = trimmed.split(String.valueOf(COLON), 2);
        if (parts.length != 2) {
            return;
        }
        String[] tokens = parts[1].trim().split(SPACE_REGEX);
        if (tokens.length < 3) {
            return;
        }
        String extreme = parts[0].trim().replace(SPACE_CHAR, UNDERSCORE);
        tags.add(PIECE_EXTREME_PREFIX + extreme + SIDE_FIELD + tokens[0] + SPACE_TEXT + PIECE_KEY + EQUAL_SIGN
                + tokens[1] + SQUARE_FIELD + tokens[2]);
    }

    /**
     * Adds piece-activity tags and outpost tags parsed from activity descriptions.
     *
     * @param tags the mutable tag accumulator
     * @param trimmed the trimmed piece-related tag text
     */
    private static void addPieceActivityTag(List<String> tags, String trimmed) {
        if (trimmed.startsWith(OUTPOST_PREFIX)) {
            addParsedPieceTag(tags, trimmed.substring(OUTPOST_PREFIX.length()).trim(), null, OUTPOST_TAG_PREFIX);
            return;
        }
        addActivityTag(tags, trimmed, PINNED_SUFFIX, PIECE_ACTIVITY_PREFIX + PIN);
        addActivityTag(tags, trimmed, TRAPPED_PREFIX, PIECE_ACTIVITY_PREFIX + TRAPPED);
        addActivityTag(tags, trimmed, LOW_MOBILITY_PREFIX, PIECE_ACTIVITY_PREFIX + LOW_MOBILITY);
        addActivityTag(tags, trimmed, HIGH_MOBILITY_PREFIX, PIECE_ACTIVITY_PREFIX + HIGH_MOBILITY);
    }

    /**
     * Applies a single activity marker to a piece tag description.
     *
     * @param tags the mutable tag accumulator
     * @param trimmed the trimmed piece-related tag text
     * @param marker the marker to detect at either end of the string
     * @param prefix the output prefix to apply when a match is found
     */
    private static void addActivityTag(List<String> tags, String trimmed, String marker, String prefix) {
        String text = null;
        if (trimmed.endsWith(marker)) {
            text = trimmed.substring(0, trimmed.length() - marker.length());
        } else if (trimmed.startsWith(marker)) {
            text = trimmed.substring(marker.length());
        }
        if (text != null) {
            addParsedPieceTag(tags, text.trim(), prefix, null);
        }
    }

    /**
     * Adds a parsed piece tag or outpost tag from the supplied raw text.
     *
     * @param tags the mutable tag accumulator
     * @param text the piece description text
     * @param activityPrefix the prefix to use for activity tags, or {@code null}
     * @param outpostPrefix the prefix to use for outpost tags, or {@code null}
     */
    private static void addParsedPieceTag(List<String> tags, String text, String activityPrefix, String outpostPrefix) {
        ParsedPieceInfo info = parsePieceInfo(text);
        if (info == null) {
            return;
        }
        if (activityPrefix != null) {
            tags.add(activityPrefix + SIDE_FIELD + info.side + SPACE_TEXT + PIECE_KEY + EQUAL_SIGN + info.piece
                    + SQUARE_FIELD + info.square);
        } else {
            tags.add(outpostPrefix + info.side + SQUARE_FIELD + info.square + SPACE_TEXT + PIECE_KEY + EQUAL_SIGN
                    + info.piece);
        }
    }

    /**
     * Adds pawn-structure tags derived from the pawn-structure subsystem.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addPawnStructureTags(List<String> tags, Position position) {
        for (String tag : PawnStructure.tags(position)) {
            String pawnStructureTag = pawnStructureTag(tag);
            if (pawnStructureTag != null) {
                tags.add(pawnStructureTag);
            }
        }
    }

    /**
     * Normalizes a pawn-structure tag into the canonical pawn-family form.
     *
     * @param tag the raw pawn-structure tag text
     * @return the normalized pawn-family tag, or {@code null} if the input is not usable
     */
    private static String pawnStructureTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String trimmed = tag.trim();
        if (!trimmed.startsWith(FACT_PAWN_STRUCTURE_PREFIX)) {
            return null;
        }
        Map<String, String> kv = parseKeyValues(trimmed.substring(FACT_HEADER.length()).trim());
        String type = kv.get(TYPE);
        String side = kv.get(SIDE);
        if (type == null || side == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(64);
        sb.append(PAWN_FAMILY).append(COLON_SPACE).append(STRUCTURE).append(EQUAL_SIGN).append(type)
                .append(SIDE_FIELD).append(side);
        appendIfPresent(sb, SQUARE, kv.get(SQUARE));
        appendIfPresent(sb, FILE, kv.get(FILE));
        appendIfPresent(sb, SQUARES, kv.get(SQUARES));
        return sb.toString();
    }

    /**
     * Adds pawn-island and pawn-majority tags.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addPawnStatTags(List<String> tags, Position position) {
        PawnStats stats = PawnStats.from(position);
        tags.add(PAWN_ISLANDS_PREFIX + WHITE + COUNT_FIELD + stats.whiteIslands);
        tags.add(PAWN_ISLANDS_PREFIX + BLACK + COUNT_FIELD + stats.blackIslands);
        addMajorityTag(tags, stats.whiteMajority, WHITE);
        addMajorityTag(tags, stats.blackMajority, BLACK);
    }

    /**
     * Adds a pawn-majority tag when a majority exists.
     *
     * @param tags the mutable tag accumulator
     * @param majority the majority label to emit
     * @param side the side owning the majority
     */
    private static void addMajorityTag(List<String> tags, String majority, String side) {
        if (majority != null) {
            tags.add(PAWN_MAJORITY_PREFIX + majority + SIDE_FIELD + side);
        }
    }

    /**
     * Adds promotion-threat tags and records whether either side has a threat.
     *
     * @param tags the mutable tag accumulator
     * @param ctx the shared tagging context
     * @param position the position being tagged
     */
    private static void addPromotionThreatTags(List<String> tags, TagContext ctx, Position position) {
        for (String tag : Promotion.tags(position)) {
            String side = addPromotionThreatTag(tags, tag);
            if (WHITE.equals(side)) {
                ctx.hasThreatWhite = true;
            } else if (BLACK.equals(side)) {
                ctx.hasThreatBlack = true;
            }
        }
    }

    /**
     * Adds a single promotion-threat tag from the raw promotion output.
     *
     * @param tags the mutable tag accumulator
     * @param tag the raw promotion tag text
     * @return the threatened side, or {@code null} if the tag did not match
     */
    private static String addPromotionThreatTag(List<String> tags, String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String trimmed = tag.trim();
        if (!trimmed.startsWith(FACT_PROMOTION_AVAILABLE_PREFIX)) {
            return null;
        }
        Map<String, String> kv = parseKeyValues(trimmed.substring(FACT_HEADER.length()).trim());
        String side = kv.get(SIDE);
        if (side == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(64);
        sb.append(THREAT_PROMOTION_PREFIX).append(side).append(SPACE_TEXT).append(SEVERITY).append(EQUAL_SIGN)
                .append(IMMEDIATE);
        appendIfPresent(sb, SQUARE, kv.get(SQUARE));
        tags.add(sb.toString());
        return side;
    }

    /**
     * Adds tactical motif tags based on the tactical analysis text.
     *
     * @param tags the mutable tag accumulator
     * @param trimmed the trimmed tactical tag text
     */
    private static void addTacticalTag(List<String> tags, String trimmed) {
        if (!trimmed.startsWith(FACT_TACTICAL_PREFIX)) {
            return;
        }
        String text = extractQuoted(trimmed);
        if (text == null || text.isBlank()) {
            return;
        }
        String motif = tacticalMotif(text);
        if (motif == null) {
            return;
        }
        StringBuilder sb = new StringBuilder(96);
        sb.append(TACTIC_MOTIF_PREFIX).append(motif);
        String side = leadingColor(text);
        if (side != null) {
            sb.append(SIDE_FIELD).append(side);
        }
        sb.append(TACTIC_DETAIL_FIELD).append(escape(text)).append(QUOTE);
        tags.add(sb.toString());
    }

    /**
     * Adds normalized endgame tags from the endgame subsystem.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addEndgameTags(List<String> tags, Position position) {
        for (String tag : Endgame.tags(position)) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.startsWith(FACT_ENDGAME_PREFIX)) {
                String val = valueAfter(trimmed, FACT_ENDGAME_PREFIX);
                if (val != null) {
                    tags.add(ENDGAME_TYPE_PREFIX + mapEndgame(val));
                }
            } else if (trimmed.startsWith(FACT_OPPOSITE_COLORED_BISHOPS_PREFIX)) {
                tags.add(ENDGAME_TYPE_PREFIX + OPPOSITE_BISHOPS);
            }
        }
    }

    /**
     * Adds normalized opening tags from the opening subsystem.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addOpeningTags(List<String> tags, Position position) {
        for (String tag : Opening.tags(position)) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.startsWith(META_ECO_PREFIX)) {
                String eco = valueAfter(trimmed, META_ECO_PREFIX);
                if (eco != null) {
                    tags.add(OPENING_ECO_PREFIX + eco);
                }
            } else if (trimmed.startsWith(META_OPENING_PREFIX)) {
                String name = extractQuoted(trimmed);
                if (name != null) {
                    tags.add(OPENING_NAME_PREFIX + escape(name) + QUOTE);
                }
            }
        }
    }

    /**
     * Adds piece tags derived from ablation and activity analyzers.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     * @param evaluator the evaluator used by the ablation analyzer
     */
    private static void addPieceTags(List<String> tags, Position position, Evaluator evaluator) {
        for (String tag : PieceAblation.tag(position, evaluator, true)) {
            if (tag != null && !tag.isBlank()) {
                addPieceTierTag(tags, tag.trim());
            }
        }

        for (String tag : PieceActivity.tags(position)) {
            if (tag != null && !tag.isBlank()) {
                addPieceActivityTag(tags, tag.trim());
            }
        }
    }

    /**
     * Adds all pawn-related tags for the position.
     *
     * @param tags the mutable tag accumulator
     * @param ctx the shared tagging context
     * @param position the position being tagged
     */
    private static void addPawnTags(List<String> tags, TagContext ctx, Position position) {
        addPawnStructureTags(tags, position);
        addPawnStatTags(tags, position);
        addPromotionThreatTags(tags, ctx, position);
    }

    /**
     * Adds king-safety tags for both sides when the position is not Chess960.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addKingTags(List<String> tags, Position position) {
        if (position.isChess960()) {
            return;
        }
        List<String> safety = chess.tag.position.KingSafety.tags(position);
        KingSafety white = new KingSafety();
        KingSafety black = new KingSafety();
        for (String tag : safety) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.startsWith(WHITE)) {
                applyKingSafetyToken(white, trimmed);
            } else if (trimmed.startsWith(BLACK)) {
                applyKingSafetyToken(black, trimmed);
            } else if (trimmed.startsWith(OPEN_FILE_NEAR_WHITE_KING)) {
                white.openFile = true;
            } else if (trimmed.startsWith(OPEN_FILE_NEAR_BLACK_KING)) {
                black.openFile = true;
            }
        }
        addKingSafetyTags(tags, WHITE, white);
        addKingSafetyTags(tags, BLACK, black);
    }

    /**
     * Adds tactical tags produced by the tactical analyzer.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addTacticalTags(List<String> tags, Position position) {
        for (String tag : Tactical.tags(position)) {
            if (tag != null && !tag.isBlank()) {
                addTacticalTag(tags, tag.trim());
            }
        }
    }

    /**
     * Adds strategic tags based on space, development, mobility, and initiative.
     *
     * @param tags the mutable tag accumulator
     * @param ctx the shared tagging context
     * @param position the position being tagged
     */
    private static void addStrategicTags(List<String> tags, TagContext ctx, Position position) {
        String spaceSide = spaceSide(ctx);
        if (spaceSide != null) {
            tags.add(SPACE_SIDE_PREFIX + spaceSide);
        }

        String developmentSide = developmentSide(position);
        if (developmentSide != null) {
            tags.add(DEVELOPMENT_SIDE_PREFIX + developmentSide);
        }

        String mobilitySide = mobilitySide(position);
        if (mobilitySide != null) {
            tags.add(MOBILITY_SIDE_PREFIX + mobilitySide);
        }

        String initiativeSide = initiativeSide(ctx);
        if (initiativeSide != null) {
            tags.add(INITIATIVE_SIDE_PREFIX + initiativeSide);
        }
    }

    /**
     * Adds the endgame and opening tag families.
     *
     * @param tags the mutable tag accumulator
     * @param position the position being tagged
     */
    private static void addEndgameOpening(List<String> tags, Position position) {
        addEndgameTags(tags, position);
        addOpeningTags(tags, position);
    }

    /**
     * Labels the game phase from the total material remaining.
     *
     * @param position the position being evaluated
     * @return the phase label, such as opening, middlegame, or endgame
     */
    private static String phaseLabel(Position position) {
        int total = position.countTotalMaterial();
        double phase = Math.max(0.0, Math.min(1.0, total / (double) START_TOTAL_MATERIAL));
        if (phase >= PHASE_OPENING) {
            return OPENING_LOWER;
        }
        if (phase >= PHASE_MIDDLEGAME) {
            return MIDDLEGAME;
        }
        return ENDGAME_LOWER;
    }

    /**
     * Labels the current board status.
     *
     * @param position the position being evaluated
     * @return the status label for the position
     */
    private static String statusLabel(Position position) {
        if (position.isCheckmate()) {
            return CHECKMATED;
        }
        MoveList moves = position.legalMoves();
        if (moves.isEmpty()) {
            return position.inCheck() ? CHECKMATED : STALEMATE;
        }
        if (position.inCheck()) {
            return CHECK;
        }
        return NORMAL;
    }

    /**
     * Formats the available castling rights into a compact label.
     *
     * @param position the position being evaluated
     * @return the castling-rights label, or {@code null} when no rights remain
     */
    private static String castleRightsLabel(Position position) {
        StringBuilder sb = new StringBuilder(4);
        if (position.activeCastlingMoveTarget(Position.WHITE_KINGSIDE) != Field.NO_SQUARE) {
            sb.append(WHITE_KINGSIDE_RIGHT);
        }
        if (position.activeCastlingMoveTarget(Position.WHITE_QUEENSIDE) != Field.NO_SQUARE) {
            sb.append(WHITE_QUEENSIDE_RIGHT);
        }
        if (position.activeCastlingMoveTarget(Position.BLACK_KINGSIDE) != Field.NO_SQUARE) {
            sb.append(BLACK_KINGSIDE_RIGHT);
        }
        if (position.activeCastlingMoveTarget(Position.BLACK_QUEENSIDE) != Field.NO_SQUARE) {
            sb.append(BLACK_QUEENSIDE_RIGHT);
        }
        if (sb.isEmpty()) {
            return NONE;
        }
        return sb.toString();
    }

    /**
     * Converts a material discrepancy into a coarse balance label.
     *
     * @param discrepancy the material difference from White's perspective
     * @return the balance label describing the material edge
     */
    private static String balanceLabel(int discrepancy) {
        if (Math.abs(discrepancy) < 50) {
            return EQUAL;
        }
        String side = discrepancy > 0 ? WHITE : BLACK;
        int abs = Math.abs(discrepancy);
        if (abs < 150) {
            return side + UP_PAWN_SUFFIX;
        }
        if (abs < 350) {
            return side + UP_MINOR_SUFFIX;
        }
        if (abs < 650) {
            return side + UP_EXCHANGE_SUFFIX;
        }
        return side + UP_QUEEN_SUFFIX;
    }

    /**
     * Converts a WDL estimate into a coarse difficulty label.
     *
     * @param wdl the win-draw-loss estimate
     * @return the human-readable difficulty label, or {@code null} if unavailable
     */
    private static String difficultyLabel(Wdl wdl) {
        if (wdl == null) {
            return null;
        }
        double expected = (wdl.win() + 0.5 * wdl.draw()) / Wdl.TOTAL;
        double difficulty = logarithmicDifficulty(expected);
        if (difficulty <= 0.20) {
            return VERY_EASY;
        }
        if (difficulty <= 0.35) {
            return EASY;
        }
        if (difficulty <= 0.55) {
            return MEDIUM;
        }
        if (difficulty <= 0.70) {
            return HARD;
        }
        return VERY_HARD;
    }

    /**
     * Converts an expected score into a logarithmic difficulty score.
     *
     * @param expectedScore the expected score in the range {@code [0,1]}
     * @return a normalized difficulty value in the range {@code [0,1]}
     */
    private static double logarithmicDifficulty(double expectedScore) {
        double linear = Numbers.clamp01(1.0 - expectedScore);
        double k = 3.0;
        double difficulty = Math.log1p(k * linear) / Math.log1p(k);
        return Numbers.clamp01(difficulty);
    }

    /**
     * Extracts an evaluation from engine analysis when possible.
     *
     * @param analysis optional engine analysis
     * @return the valid evaluation, or {@code null} when no usable analysis exists
     */
    private static Evaluation evaluationFrom(Analysis analysis) {
        if (analysis == null) {
            return null;
        }
        Output output = analysis.getBestOutput();
        if (output == null) {
            return null;
        }
        Evaluation eval = output.getEvaluation();
        if (eval == null || !eval.isValid()) {
            return null;
        }
        return eval;
    }

    /**
     * Extracts a centipawn score from an analysis output when possible.
     *
     * @param output the engine output to inspect
     * @return the centipawn value, or {@code null} when unavailable
     */
    private static Integer evalFromOutput(Output output) {
        if (output == null) {
            return null;
        }
        Evaluation eval = output.getEvaluation();
        if (eval == null || !eval.isValid()) {
            return null;
        }
        if (eval.isMate()) {
            return eval.getValue() > 0 ? 100000 : -100000;
        }
        return eval.getValue();
    }

    /**
     * Extracts chances data from engine analysis when present.
     *
     * @param analysis optional engine analysis
     * @return the chances object, or {@code null} when unavailable
     */
    private static Chances chancesFrom(Analysis analysis) {
        if (analysis == null) {
            return null;
        }
        Output output = analysis.getBestOutput();
        if (output == null) {
            return null;
        }
        return output.getChances();
    }

    /**
     * Determines a centipawn evaluation using engine data, analysis data, or a fallback heuristic.
     *
     * @param evaluator the evaluator used as the primary fallback
     * @param position the position being evaluated
     * @param analysisEval the analysis evaluation, if available
     * @return a centipawn score for the position
     */
    private static Integer evalCentipawns(Evaluator evaluator, Position position, Evaluation analysisEval) {
        Integer cp = null;
        if (analysisEval != null && !analysisEval.isMate()) {
            cp = analysisEval.getValue();
        }
        if (cp == null) {
            Result result = evaluator.evaluate(position);
            if (result != null) {
                cp = result.centipawns();
            }
        }
        if (cp == null) {
            cp = Wdl.evaluateStmCentipawns(position);
        }
        return cp;
    }

    /**
     * Returns the text that follows a named prefix in a tag.
     *
     * @param tag the full tag text
     * @param key the prefix to search for
     * @return the trimmed suffix after the prefix, or {@code null} when absent
     */
    private static String valueAfter(String tag, String key) {
        int idx = tag.indexOf(key);
        if (idx < 0) {
            return null;
        }
        return tag.substring(idx + key.length()).trim();
    }

    /**
     * Extracts the text between the first pair of quotes.
     *
     * @param tag the tag text to inspect
     * @return the quoted text, or {@code null} when no quoted segment exists
     */
    private static String extractQuoted(String tag) {
        int first = tag.indexOf(QUOTE);
        if (first < 0) {
            return null;
        }
        int second = tag.indexOf(QUOTE, first + 1);
        if (second < 0) {
            return null;
        }
        return tag.substring(first + 1, second);
    }

    /**
     * Escapes backslashes and quotes for tag output.
     *
     * @param value the raw text to escape
     * @return the escaped text
     */
    private static String escape(String value) {
        return value.replace(String.valueOf(BACKSLASH), ESCAPED_BACKSLASH)
                .replace(String.valueOf(QUOTE), ESCAPED_QUOTE);
    }

    /**
     * Appends an optional field to a serialized tag buffer.
     *
     * @param sb the output buffer
     * @param label the field label to append
     * @param value the field value, if present
     */
    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null) {
            sb.append(SPACE_TEXT).append(label).append(EQUAL_SIGN).append(value);
        }
    }

    /**
     * Parses whitespace-separated key-value pairs into a map.
     *
     * @param text the text to parse
     * @return the parsed key-value map
     */
    private static Map<String, String> parseKeyValues(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return map;
        }
        String[] tokens = text.split(SPACE_REGEX);
        for (String token : tokens) {
            int eq = token.indexOf(EQUAL_SIGN);
            if (eq <= 0) {
                continue;
            }
            String key = token.substring(0, eq).trim();
            String value = token.substring(eq + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * Parses a tier-style piece tag into a structured record.
     *
     * @param tag the tag text to parse
     * @return the parsed tier record, or {@code null} if the text does not match
     */
    private static ParsedPieceTier parseTierTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String lowered = tag.toLowerCase();
        ParsedPrefix prefix = matchTierPrefix(lowered);
        if (prefix == null) {
            return null;
        }
        String[] parts = prefix.remainder.split(SPACE_REGEX);
        if (parts.length < 3) {
            return null;
        }
        return new ParsedPieceTier(prefix.value, parts[0], parts[1], parts[2]);
    }

    /**
     * Matches a tier prefix in lowercase text.
     *
     * @param lowered the lowercase text to inspect
     * @return the matched prefix, or {@code null} if none matches
     */
    private static ParsedPrefix matchTierPrefix(String lowered) {
        ParsedPrefix prefix = findPrefix(lowered,
                new ParsedPrefix(VERY_STRONG_TEXT, VERY_STRONG),
                new ParsedPrefix(STRONG, STRONG),
                new ParsedPrefix(SLIGHTLY_STRONG_TEXT, SLIGHTLY_STRONG),
                new ParsedPrefix(NEUTRAL, NEUTRAL),
                new ParsedPrefix(SLIGHTLY_WEAK_TEXT, SLIGHTLY_WEAK),
                new ParsedPrefix(VERY_WEAK_TEXT, VERY_WEAK),
                new ParsedPrefix(WEAK, WEAK));
        if (prefix == null) {
            return null;
        }
        return new ParsedPrefix(prefix.key, prefix.value, lowered.substring(prefix.key.length()).trim());
    }

    /**
     * Finds the first matching prefix and returns its associated value.
     *
     * @param text the text to inspect
     * @param prefixes the candidate prefixes
     * @return the matched prefix value, or {@code null} if none matches
     */
    private static String startsWithAny(String text, ParsedPrefix... prefixes) {
        ParsedPrefix match = findPrefix(text, prefixes);
        return match == null ? null : match.value;
    }

    /**
     * Finds the first matching prefix from a list of candidates.
     *
     * @param text the text to inspect
     * @param prefixes the candidate prefixes
     * @return the matched prefix, or {@code null} if none matches
     */
    private static ParsedPrefix findPrefix(String text, ParsedPrefix... prefixes) {
        for (ParsedPrefix prefix : prefixes) {
            if (text.startsWith(prefix.key)) {
                return prefix;
            }
        }
        return null;
    }

    /**
     * Parses a piece description into side, piece, and square fields.
     *
     * @param text the piece description text
     * @return the parsed piece info, or {@code null} if the text is incomplete
     */
    private static ParsedPieceInfo parsePieceInfo(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.trim().split(SPACE_REGEX);
        if (parts.length < 3) {
            return null;
        }
        String side = parts[0];
        String piece = parts[1];
        String square = parts[2];
        return new ParsedPieceInfo(side, piece, square);
    }

    /**
     * Detects the tactical motif name from a free-form tactical description.
     *
     * @param text the tactical description text
     * @return the motif label, or {@code null} when no known motif is found
     */
    private static String tacticalMotif(String text) {
        String lowered = text.toLowerCase();
        return startsWithAny(lowered,
                new ParsedPrefix(PIN_HEADER, PIN),
                new ParsedPrefix(SKEWER_HEADER, SKEWER),
                new ParsedPrefix(DISCOVERED_ATTACK_HEADER, DISCOVERED_ATTACK),
                new ParsedPrefix(OVERLOADED_DEFENDER_HEADER, OVERLOAD),
                new ParsedPrefix(HANGING_PREFIX, HANGING));
    }

    /**
     * Extracts a leading color token from a free-form text.
     *
     * @param text the text to inspect
     * @return {@code white} or {@code black} when present, otherwise {@code null}
     */
    private static String leadingColor(String text) {
        String[] tokens = text.split(SPACE_REGEX);
        for (String token : tokens) {
            if (WHITE.equals(token)) {
                return WHITE;
            }
            if (BLACK.equals(token)) {
                return BLACK;
            }
        }
        return null;
    }

    /**
     * Applies king-safety markers to the aggregated king-safety state.
     *
     * @param safety the mutable safety state to update
     * @param token the normalized token text
     */
    private static void applyKingSafetyToken(KingSafety safety, String token) {
        if (token.contains(CASTLED)) {
            safety.castled = true;
        }
        if (token.contains(UNCASTLED)) {
            safety.castled = false;
        }
        if (token.contains(PAWN_SHIELD_WEAKENED)) {
            safety.shieldWeakened = true;
        }
        if (token.contains(KING_EXPOSED)) {
            safety.exposed = true;
        }
        if (token.contains(OPEN_FILE_NEAR)) {
            safety.openFile = true;
        }
    }

    /**
     * Emits the final king-safety tags for one side.
     *
     * @param tags the mutable tag accumulator
     * @param side the side label to attach
     * @param safety the aggregated safety state
     */
    private static void addKingSafetyTags(List<String> tags, String side, KingSafety safety) {
        boolean castled = Boolean.TRUE.equals(safety.castled);
        if (safety.castled != null) {
            tags.add(KING_CASTLED_PREFIX + (castled ? YES : NO) + SIDE_FIELD + side);
        }
        String shelter = PAWNS_INTACT;
        if (safety.openFile) {
            shelter = OPEN;
        } else if (safety.shieldWeakened) {
            shelter = WEAKENED;
        }
        tags.add(KING_SHELTER_PREFIX + shelter + SIDE_FIELD + side);

        String safetyLabel = SAFE;
        if (safety.exposed) {
            safetyLabel = VERY_UNSAFE;
        } else if (safety.openFile || safety.shieldWeakened) {
            safetyLabel = UNSAFE;
        } else if (castled) {
            safetyLabel = VERY_SAFE;
        }
        tags.add(KING_SAFETY_PREFIX + safetyLabel + SIDE_FIELD + side);
    }

    /**
     * Checks whether the board contains bishops on opposite colors.
     *
     * @param board the board array to inspect
     * @return {@code true} when both bishop colors occupy opposite-colored squares
     */
    private static boolean hasOppositeColoredBishops(byte[] board) {
        BishopColorState bishops = new BishopColorState();
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (Piece.isBishop(piece)) {
                bishops.mark(piece, (byte) index);
            }
        }
        return bishops.hasOppositeColors();
    }

    /**
     * Determines whether the position has insufficient mating material.
     *
     * @param position the position to inspect
     * @return {@code true} when the remaining material cannot force mate
     */
    private static boolean isInsufficientMaterial(Position position) {
        return position.isInsufficientMaterial();
    }

    /**
     * Maps verbose endgame labels to the canonical endgame tag values.
     *
     * @param val the raw endgame label
     * @return the canonical endgame label
     */
    private static String mapEndgame(String val) {
        switch (val) {
            case QUEENLESS:
                return QUEENLESS;
            case ROOK_ENDGAME:
                return ROOK_ENDGAME_SHORT;
            case MINOR_PIECE_ENDGAME:
                return MINOR_ENDGAME_SHORT;
            default:
                return val;
        }
    }

    /**
     * Determines the side with a space advantage.
     *
     * @param ctx the shared tagging context
     * @return the side with the advantage, or equal when no preference exists
     */
    private static String spaceSide(TagContext ctx) {
        if (WHITE.equals(ctx.spaceAdvantage) || BLACK.equals(ctx.spaceAdvantage)) {
            return ctx.spaceAdvantage;
        }
        if (ctx.centerControl != null) {
            if (WHITE.equals(ctx.centerControl)) {
                return WHITE;
            }
            if (BLACK.equals(ctx.centerControl)) {
                return BLACK;
            }
        }
        return EQUAL;
    }

    /**
     * Determines the side with a development advantage.
     *
     * @param position the position to inspect
     * @return the side with the advantage, or equal when no preference exists
     */
    private static String developmentSide(Position position) {
        int white = undevelopedMinors(position, true);
        int black = undevelopedMinors(position, false);
        int diff = black - white;
        if (diff >= 2) {
            return WHITE;
        }
        if (diff <= -2) {
            return BLACK;
        }
        return EQUAL;
    }

    /**
     * Determines the side with a mobility advantage.
     *
     * @param position the position to inspect
     * @return the side with the advantage, or equal when no preference exists
     */
    private static String mobilitySide(Position position) {
        int whiteMoves = mobilityForSide(position, true);
        int blackMoves = mobilityForSide(position, false);
        int diff = whiteMoves - blackMoves;
        if (diff >= 5) {
            return WHITE;
        }
        if (diff <= -5) {
            return BLACK;
        }
        return EQUAL;
    }

    /**
     * Determines the side with the initiative.
     *
     * @param ctx the shared tagging context
     * @return the side that appears to have the initiative, or equal when unclear
     */
    private static String initiativeSide(TagContext ctx) {
        if (ctx.hasThreatWhite && !ctx.hasThreatBlack) {
            return WHITE;
        }
        if (ctx.hasThreatBlack && !ctx.hasThreatWhite) {
            return BLACK;
        }
        if (ctx.evalCpWhite != null) {
            if (ctx.evalCpWhite > 80) {
                return WHITE;
            }
            if (ctx.evalCpWhite < -80) {
                return BLACK;
            }
        }
        return EQUAL;
    }

    /**
     * Counts undeveloped minor pieces for a given side.
     *
     * @param position the position to inspect
     * @param white whether to count White pieces or Black pieces
     * @return the number of undeveloped minor pieces
     */
    private static int undevelopedMinors(Position position, boolean white) {
        int count = 0;
        byte[] board = position.getBoard();
        if (white) {
            count += pieceCountAt(board, Piece.WHITE_KNIGHT, Field.B1);
            count += pieceCountAt(board, Piece.WHITE_KNIGHT, Field.G1);
            count += pieceCountAt(board, Piece.WHITE_BISHOP, Field.C1);
            count += pieceCountAt(board, Piece.WHITE_BISHOP, Field.F1);
        } else {
            count += pieceCountAt(board, Piece.BLACK_KNIGHT, Field.B8);
            count += pieceCountAt(board, Piece.BLACK_KNIGHT, Field.G8);
            count += pieceCountAt(board, Piece.BLACK_BISHOP, Field.C8);
            count += pieceCountAt(board, Piece.BLACK_BISHOP, Field.F8);
        }
        return count;
    }

    /**
     * Checks whether a specific piece occupies a specific square.
     *
     * @param board the board array to inspect
     * @param piece the piece to match
     * @param square the square to inspect
     * @return {@code true} when the board square contains the piece
     */
    private static boolean isPieceAt(byte[] board, byte piece, byte square) {
        if (square == Field.NO_SQUARE) {
            return false;
        }
        return board[square] == piece;
    }

    /**
     * Returns 1 when a piece is on a given square and 0 otherwise.
     *
     * @param board the board array to inspect
     * @param piece the piece to match
     * @param square the square to inspect
     * @return 1 when the piece occupies the square, otherwise 0
     */
    private static int pieceCountAt(byte[] board, byte piece, byte square) {
        return isPieceAt(board, piece, square) ? 1 : 0;
    }

    /**
     * Counts legal moves for one side, even when that side is not on move.
     *
     * @param position the position to inspect
     * @param white whether to count White's mobility or Black's mobility
     * @return the number of legal moves for the requested side
     */
    private static int mobilityForSide(Position position, boolean white) {
        if (position.isWhiteToMove() == white) {
            return position.legalMoves().size();
        }
        String flipped = flipSideToMove(position.toString(), white);
        if (flipped == null) {
            return position.legalMoves().size();
        }
        try {
            Position other = new Position(flipped);
            return other.legalMoves().size();
        } catch (IllegalArgumentException ex) {
            return position.legalMoves().size();
        }
    }

    /**
     * Flips the side-to-move field in a FEN string.
     *
     * @param fen the source FEN text
     * @param white whether the resulting FEN should indicate White to move
     * @return the rewritten FEN, or {@code null} when the input is malformed
     */
    private static String flipSideToMove(String fen, boolean white) {
        String[] parts = fen.split(SPACE_REGEX);
        if (parts.length < 2) {
            return null;
        }
        parts[1] = white ? FEN_WHITE_TO_MOVE : FEN_BLACK_TO_MOVE;
        return String.join(SPACE_TEXT, parts);
    }

    /**
     * Holds the parsed tier components for a piece ablation tag.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class ParsedPieceTier {

        /**
         * The tier label.
         */
        private final String tier;

        /**
         * The side label.
         */
        private final String side;

        /**
         * The piece label.
         */
        private final String piece;

        /**
         * The square label.
         */
        private final String square;

        /**
         * Creates a parsed piece tier record.
         *
         * @param tier the tier label
         * @param side the side label
         * @param piece the piece label
         * @param square the square label
         */
        private ParsedPieceTier(String tier, String side, String piece, String square) {
            this.tier = tier;
            this.side = side;
            this.piece = piece;
            this.square = square;
        }
    }

    /**
     * Holds the parsed side, piece, and square components for piece activity tags.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class ParsedPieceInfo {

        /**
         * The side label.
         */
        private final String side;

        /**
         * The piece label.
         */
        private final String piece;

        /**
         * The square label.
         */
        private final String square;

        /**
         * Creates a parsed piece-info record.
         *
         * @param side the side label
         * @param piece the piece label
         * @param square the square label
         */
        private ParsedPieceInfo(String side, String piece, String square) {
            this.side = side;
            this.piece = piece;
            this.square = square;
        }
    }

    /**
     * Captures king-safety properties while folding multiple tokens together.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class KingSafety {

        /**
         * Whether castling status was observed.
         */
        private Boolean castled;

        /**
         * Whether the pawn shield appears weakened.
         */
        private boolean shieldWeakened;

        /**
         * Whether the king appears exposed.
         */
        private boolean exposed;

        /**
         * Whether an open file is near the king.
         */
        private boolean openFile;
    }

    /**
     * Represents a candidate prefix and its normalized value.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class ParsedPrefix {

        /**
         * The prefix text to match.
         */
        private final String key;

        /**
         * The value associated with the prefix.
         */
        private final String value;

        /**
         * The remainder of the string after the prefix.
         */
        private final String remainder;

        /**
         * Creates a prefix/value pair without a remainder.
         *
         * @param key the prefix text
         * @param value the normalized value
         */
        private ParsedPrefix(String key, String value) {
            this(key, value, null);
        }

        /**
         * Creates a prefix/value pair with a parsed remainder.
         *
         * @param key the prefix text
         * @param value the normalized value
         * @param remainder the remaining text after the prefix
         */
        private ParsedPrefix(String key, String value, String remainder) {
            this.key = key;
            this.value = value;
            this.remainder = remainder;
        }
    }

    /**
     * Tracks bishop-square colors for each side.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class BishopColorState {

        /**
         * Whether White has a bishop on a light square.
         */
        private boolean whiteLight;

        /**
         * Whether White has a bishop on a dark square.
         */
        private boolean whiteDark;

        /**
         * Whether Black has a bishop on a light square.
         */
        private boolean blackLight;

        /**
         * Whether Black has a bishop on a dark square.
         */
        private boolean blackDark;

        /**
         * Records a bishop on its square color.
         *
         * @param piece the bishop piece code
         * @param square the square index
         */
        private void mark(byte piece, byte square) {
            boolean light = isLightSquare(square);
            if (Piece.isWhite(piece)) {
                whiteLight |= light;
                whiteDark |= !light;
            } else {
                blackLight |= light;
                blackDark |= !light;
            }
        }

        /**
         * Checks whether opposite-colored bishops are present.
         *
         * @return {@code true} when the bishop colors are opposite across sides
         */
        private boolean hasOppositeColors() {
            return (whiteLight && blackDark) || (whiteDark && blackLight);
        }

        /**
         * Determines whether a square is light-colored.
         *
         * @param square the square index
         * @return {@code true} when the square is light-colored
         */
        private static boolean isLightSquare(byte square) {
            int file = Field.getX(square);
            int rank = Field.getY(square);
            return ((file + rank) & 1) == 0;
        }
    }

    /**
     * Counts minor-material pieces and detects whether major material is present.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class MinorMaterialCounts {

        /**
         * White knights on the board.
         */
        private final int whiteKnights;

        /**
         * White bishops on the board.
         */
        private final int whiteBishops;

        /**
         * Black knights on the board.
         */
        private final int blackKnights;

        /**
         * Black bishops on the board.
         */
        private final int blackBishops;

        /**
         * Creates a material-count snapshot.
         *
         * @param whiteKnights the white knight count
         * @param whiteBishops the white bishop count
         * @param blackKnights the black knight count
         * @param blackBishops the black bishop count
         */
        private MinorMaterialCounts(int whiteKnights, int whiteBishops, int blackKnights, int blackBishops) {
            this.whiteKnights = whiteKnights;
            this.whiteBishops = whiteBishops;
            this.blackKnights = blackKnights;
            this.blackBishops = blackBishops;
        }

        /**
         * Builds a material-count snapshot from the board.
         *
         * @param board the board array to inspect
         * @return the computed material-count snapshot
         */
        private static MinorMaterialCounts from(byte[] board) {
            int whiteKnights = 0;
            int whiteBishops = 0;
            int blackKnights = 0;
            int blackBishops = 0;
            for (byte piece : board) {
                if (Piece.isKnight(piece)) {
                    if (Piece.isWhite(piece)) {
                        whiteKnights++;
                    } else {
                        blackKnights++;
                    }
                } else if (Piece.isBishop(piece)) {
                    if (Piece.isWhite(piece)) {
                        whiteBishops++;
                    } else {
                        blackBishops++;
                    }
                }
            }
            return new MinorMaterialCounts(whiteKnights, whiteBishops, blackKnights, blackBishops);
        }
    }

    /**
     * Stores pawn-file and pawn-majority statistics for both sides.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class PawnStats {

        /**
         * The number of White pawn islands.
         */
        private final int whiteIslands;

        /**
         * The number of Black pawn islands.
         */
        private final int blackIslands;

        /**
         * White's majority-side label, if any.
         */
        private final String whiteMajority;

        /**
         * Black's majority-side label, if any.
         */
        private final String blackMajority;

        /**
         * Creates a pawn-statistics snapshot.
         *
         * @param whiteIslands the White pawn-island count
         * @param blackIslands the Black pawn-island count
         * @param whiteMajority the White majority label
         * @param blackMajority the Black majority label
         */
        private PawnStats(int whiteIslands, int blackIslands, String whiteMajority, String blackMajority) {
            this.whiteIslands = whiteIslands;
            this.blackIslands = blackIslands;
            this.whiteMajority = whiteMajority;
            this.blackMajority = blackMajority;
        }

        /**
         * Computes pawn statistics from the current position.
         *
         * @param position the position to inspect
         * @return the computed pawn statistics
         */
        private static PawnStats from(Position position) {
            int[] whiteFiles = new int[8];
            int[] blackFiles = new int[8];
            byte[] board = position.getBoard();
            for (int i = 0; i < board.length; i++) {
                byte piece = board[i];
                if (!Piece.isPawn(piece)) {
                    continue;
                }
                int file = Field.getX((byte) i);
                if (Piece.isWhite(piece)) {
                    whiteFiles[file]++;
                } else {
                    blackFiles[file]++;
                }
            }
            int whiteIslands = countIslands(whiteFiles);
            int blackIslands = countIslands(blackFiles);

            String whiteMajority = majoritySide(whiteFiles, blackFiles, true);
            String blackMajority = majoritySide(whiteFiles, blackFiles, false);
            return new PawnStats(whiteIslands, blackIslands, whiteMajority, blackMajority);
        }

        /**
         * Counts separate pawn islands on the file array.
         *
         * @param files the pawn counts per file
         * @return the number of contiguous pawn islands
         */
        private static int countIslands(int[] files) {
            int islands = 0;
            boolean inIsland = false;
            for (int file = 0; file < files.length; file++) {
                if (files[file] > 0) {
                    if (!inIsland) {
                        islands++;
                        inIsland = true;
                    }
                } else {
                    inIsland = false;
                }
            }
            return islands;
        }

        /**
         * Determines whether one side has a regional pawn majority.
         *
         * @param whiteFiles White pawn counts per file
         * @param blackFiles Black pawn counts per file
         * @param forWhite whether the query is for White's perspective
         * @return the majority-region label, or {@code null} when no majority exists
         */
        private static String majoritySide(int[] whiteFiles, int[] blackFiles, boolean forWhite) {
            int[] regions = new int[] { 0, 3, 5, 8 }; // a-c, d-e, f-h
            String[] labels = new String[] { QUEENSIDE, CENTER, KINGSIDE };
            String best = null;
            for (int i = 0; i < labels.length; i++) {
                int start = regions[i];
                int end = regions[i + 1];
                int white = 0;
                int black = 0;
                for (int file = start; file < end; file++) {
                    white += whiteFiles[file];
                    black += blackFiles[file];
                }
                int diff = white - black;
                if ((forWhite && diff >= 2) || (!forWhite && diff <= -2)) {
                    best = labels[i];
                }
            }
            return best;
        }
    }

    /**
     * Carries positional state across the tagging pipeline.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class TagContext {

        /**
         * The centipawn evaluation from White's perspective.
         */
        private Integer evalCpWhite;

        /**
         * The WDL snapshot used for difficulty tagging.
         */
        private Wdl wdl;

        /**
         * The center-control label extracted from center analysis.
         */
        private String centerControl;

        /**
         * The space-advantage label extracted from center analysis.
         */
        private String spaceAdvantage;

        /**
         * Whether White has any promotion threats.
         */
        private boolean hasThreatWhite;

        /**
         * Whether Black has any promotion threats.
         */
        private boolean hasThreatBlack;

        /**
         * Creates a tagging context for the given position.
         *
         * @param position the position being tagged
         */
        private TagContext(Position position) {
            Objects.requireNonNull(position, POSITION);
        }
    }
}
