package chess.tag;

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
import chess.tag.material.EndgameTagger;
import chess.tag.material.MaterialTagger;
import chess.tag.pawn.PawnStructureTagger;
import chess.tag.pawn.PromotionTagger;
import chess.tag.piece.PieceAblationTagger;
import chess.tag.piece.PieceActivityTagger;
import chess.tag.position.CenterSpaceTagger;
import chess.tag.position.KingSafetyTagger;
import chess.tag.position.OpeningTagger;
import chess.tag.tactical.TacticalTagger;
import chess.uci.Analysis;
import chess.uci.Chances;
import chess.uci.Evaluation;
import chess.uci.Output;

/**
 * Unified tagging pipeline.
 *
 * @since 2026
 */
public final class Tagging {

    private static final int START_TOTAL_MATERIAL = 8000;

    private static final double PHASE_OPENING = 0.75;

    private static final double PHASE_MIDDLEGAME = 0.35;

    private Tagging() {
        // utility
    }

    private static final class SharedEvaluator {

        private static final AtomicReference<Evaluator> INSTANCE = new AtomicReference<>();
        private static final AtomicBoolean SHUTDOWN_HOOK_INSTALLED = new AtomicBoolean(false);

        private SharedEvaluator() {
            // utility
        }

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
            }, "crtk-tag-evaluator-shutdown"));
        }
    }

    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");
        return tags(position, SharedEvaluator.get(), null);
    }

    public static List<String> tags(Position position, Analysis analysis) {
        Objects.requireNonNull(position, "position");
        return tags(position, SharedEvaluator.get(), analysis);
    }

    public static List<String> tags(Position position, Evaluator evaluator) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(evaluator, "evaluator");
        return tags(position, evaluator, null);
    }

    public static List<String> tags(Position position, Evaluator evaluator, Analysis analysis) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(evaluator, "evaluator");

        TagContext ctx = new TagContext(position);
        List<String> tags = new ArrayList<>(128);

        addMeta(tags, ctx, position, evaluator, analysis);
        addCandidatesAndPv(tags, position, analysis);
        addFacts(tags, ctx, position);
        addMaterial(tags, ctx, position);
        addPieceTags(tags, ctx, position, evaluator);
        addPawnTags(tags, ctx, position);
        addKingTags(tags, ctx, position);
        addTacticalTags(tags, ctx, position);
        addStrategicTags(tags, ctx, position);
        addEndgameOpening(tags, ctx, position);

        return TagSort.sort(tags);
    }

    public static String[] positionalTags(Position position) {
        List<String> tags = tags(position);
        return tags.toArray(new String[0]);
    }

    private static void addMeta(List<String> tags, TagContext ctx, Position position, Evaluator evaluator,
            Analysis analysis) {
        tags.add("META: to_move=" + (position.isWhiteTurn() ? "white" : "black"));

        String phase = phaseLabel(position);
        if (phase != null) {
            tags.add("META: phase=" + phase);
        }

        Evaluation analysisEval = evaluationFrom(analysis);
        if (analysisEval != null && analysisEval.isMate() && analysisEval.getValue() != 0) {
            int mate = analysisEval.getValue();
            if (mate > 0) {
                tags.add("META: mate_in=" + Math.abs(mate));
            } else {
                tags.add("META: mated_in=" + Math.abs(mate));
            }
        } else {
            Integer cp = evalCentipawns(evaluator, position, analysisEval);
            if (cp != null) {
                ctx.evalCpStm = cp;
                ctx.evalCpWhite = position.isWhiteTurn() ? cp : -cp;
                tags.add("META: eval_cp=" + cp);
                addEvalBucket(tags, ctx.evalCpWhite);
            }
        }

        Chances chances = chancesFrom(analysis);
        if (chances != null) {
            tags.add("META: wdl=" + chances.getWinChance() + "/" + chances.getDrawChance() + "/"
                    + chances.getLossChance());
            ctx.wdl = new Wdl(chances.getWinChance(), chances.getDrawChance(), chances.getLossChance());
        } else {
            Result result = evaluator.evaluate(position);
            if (result != null) {
                Wdl wdl = result.wdl();
                if (wdl != null) {
                    tags.add("META: wdl=" + wdl.win() + "/" + wdl.draw() + "/" + wdl.loss());
                    ctx.wdl = wdl;
                }
            }
        }

        if (ctx.wdl != null) {
            tags.add("META: difficulty=" + difficultyLabel(ctx.wdl));
        }

        tags.add("META: source=" + (analysis != null ? "analysis" : "engine"));
    }

    private static void addCandidatesAndPv(List<String> tags, Position position, Analysis analysis) {
        if (analysis == null || analysis.isEmpty()) {
            return;
        }
        addCandidate(tags, position, analysis, 1, "best");
        if (analysis.getPivots() >= 2) {
            addCandidate(tags, position, analysis, 2, "alt");
        }
        addPv(tags, position, analysis);
    }

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
        sb.append("CAND: role=").append(role).append(" move=").append(san);
        if (evalCp != null) {
            sb.append(" eval_cp=").append(evalCp);
        }
        sb.append(" note=\"\"");
        tags.add(sb.toString());
    }

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
        Position cursor = position.copyOf();
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
        tags.add("PV: " + String.join(" ", sanMoves));
    }

    private static void addEvalBucket(List<String> tags, Integer evalCpWhite) {
        if (evalCpWhite == null) {
            return;
        }
        int cp = evalCpWhite;
        int abs = Math.abs(cp);
        if (abs < 80) {
            tags.add("META: eval_bucket=equal");
            return;
        }
        String side = cp > 0 ? "white" : "black";
        String bucket;
        if (abs < 250) {
            bucket = "slight_" + side;
        } else if (abs < 700) {
            bucket = "clear_" + side;
        } else if (abs < 900) {
            bucket = "winning_" + side;
        } else {
            bucket = "crushing_" + side;
        }
        tags.add("META: eval_bucket=" + bucket);
    }

    private static void addFacts(List<String> tags, TagContext ctx, Position position) {
        String status = statusLabel(position);
        if ("normal".equals(status) && isInsufficientMaterial(position)) {
            status = "insufficient";
        }
        tags.add("FACT: status=" + status);
        if (position.inCheck()) {
            tags.add("FACT: in_check=" + (position.isWhiteTurn() ? "white" : "black"));
        } else {
            tags.add("FACT: in_check=none");
        }

        String castleRights = castleRightsLabel(position);
        if (castleRights != null) {
            tags.add("FACT: castle_rights=" + castleRights);
        }

        byte enPassant = position.getEnPassant();
        if (enPassant != Field.NO_SQUARE) {
            tags.add("FACT: en_passant=" + Text.squareNameLower(enPassant));
        }

        List<String> center = CenterSpaceTagger.tags(position);
        for (String tag : center) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.startsWith("FACT: center_control")) {
                String val = valueAfter(trimmed, "center_control=");
                if (val != null) {
                    ctx.centerControl = val;
                    tags.add("FACT: center_control=" + val);
                }
            } else if (trimmed.startsWith("FACT: space_advantage")) {
                String val = valueAfter(trimmed, "space_advantage=");
                if (val != null) {
                    ctx.spaceAdvantage = val;
                    tags.add("FACT: space_advantage=" + val);
                }
            } else if (trimmed.startsWith("FACT: center_state")) {
                String val = valueAfter(trimmed, "center_state=");
                if (val != null) {
                    tags.add("FACT: center_state=" + val);
                }
            }
        }

    }

    private static void addMaterial(List<String> tags, TagContext ctx, Position position) {
        int discrepancy = position.materialDiscrepancy();
        tags.add("MATERIAL: balance=" + balanceLabel(discrepancy));

        int whiteBishops = position.countPieces(Piece.WHITE_BISHOP);
        int blackBishops = position.countPieces(Piece.BLACK_BISHOP);
        int whiteQueens = position.countPieces(Piece.WHITE_QUEEN);
        int blackQueens = position.countPieces(Piece.BLACK_QUEEN);
        int whiteRooks = position.countPieces(Piece.WHITE_ROOK);
        int blackRooks = position.countPieces(Piece.BLACK_ROOK);

        if (whiteBishops >= 2) {
            tags.add("MATERIAL: imbalance=bishop_pair_white");
        }
        if (blackBishops >= 2) {
            tags.add("MATERIAL: imbalance=bishop_pair_black");
        }
        if (whiteQueens + blackQueens == 0) {
            tags.add("MATERIAL: imbalance=queenless");
        }
        if (whiteRooks + blackRooks == 0) {
            tags.add("MATERIAL: imbalance=rookless");
        }
        if (whiteBishops > 0 && blackBishops > 0) {
            if (hasOppositeColoredBishops(position.getBoard())) {
                tags.add("MATERIAL: imbalance=opposite_color_bishops");
            } else {
                tags.add("MATERIAL: imbalance=same_color_bishops");
            }
        }

        List<String> materialTags = MaterialTagger.tags(position);
        for (String tag : materialTags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.startsWith("FACT: piece_count")) {
                Map<String, String> kv = parseKeyValues(trimmed.substring("FACT:".length()).trim());
                String side = kv.get("side");
                String piece = kv.get("piece");
                String count = kv.get("count");
                if (side != null && piece != null && count != null) {
                    tags.add("MATERIAL: piece_count side=" + side + " piece=" + piece + " count=" + count);
                }
            }
        }
    }

    private static void addPieceTags(List<String> tags, TagContext ctx, Position position, Evaluator evaluator) {
        List<String> tiers = PieceAblationTagger.tag(position, evaluator, true);
        for (String tag : tiers) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.contains(": ")) {
                String[] parts = trimmed.split(":", 2);
                if (parts.length == 2) {
                    String label = parts[0].trim();
                    String rest = parts[1].trim();
                    String[] tokens = rest.split("\\s+");
                    if (tokens.length >= 3) {
                        String side = tokens[0];
                        String piece = tokens[1];
                        String square = tokens[2];
                        String extreme = label.replace(' ', '_');
                        tags.add("PIECE: extreme=" + extreme + " side=" + side + " piece=" + piece
                                + " square=" + square);
                    }
                }
                continue;
            }
            ParsedPieceTier parsed = parseTierTag(trimmed);
            if (parsed != null) {
                tags.add("PIECE: tier=" + parsed.tier + " side=" + parsed.side + " piece=" + parsed.piece
                        + " square=" + parsed.square);
            }
        }

        List<String> activity = PieceActivityTagger.tags(position);
        for (String tag : activity) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.startsWith("outpost:")) {
                ParsedPieceInfo info = parsePieceInfo(trimmed.substring("outpost:".length()).trim());
                if (info != null) {
                    tags.add("OUTPOST: side=" + info.side + " square=" + info.square + " piece=" + info.piece);
                }
            } else if (trimmed.endsWith(" pinned")) {
                ParsedPieceInfo info = parsePieceInfo(trimmed.substring(0, trimmed.length() - " pinned".length()));
                if (info != null) {
                    tags.add("PIECE: activity=pinned side=" + info.side + " piece=" + info.piece + " square="
                            + info.square);
                }
            } else if (trimmed.startsWith("trapped ")) {
                ParsedPieceInfo info = parsePieceInfo(trimmed.substring("trapped ".length()));
                if (info != null) {
                    tags.add("PIECE: activity=trapped side=" + info.side + " piece=" + info.piece + " square="
                            + info.square);
                }
            } else if (trimmed.startsWith("low mobility ")) {
                ParsedPieceInfo info = parsePieceInfo(trimmed.substring("low mobility ".length()));
                if (info != null) {
                    tags.add("PIECE: activity=low_mobility side=" + info.side + " piece=" + info.piece
                            + " square=" + info.square);
                }
            } else if (trimmed.startsWith("high mobility ")) {
                ParsedPieceInfo info = parsePieceInfo(trimmed.substring("high mobility ".length()));
                if (info != null) {
                    tags.add("PIECE: activity=high_mobility side=" + info.side + " piece=" + info.piece
                            + " square=" + info.square);
                }
            }
        }
    }

    private static void addPawnTags(List<String> tags, TagContext ctx, Position position) {
        List<String> pawn = PawnStructureTagger.tags(position);
        for (String tag : pawn) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.startsWith("FACT: pawn_structure")) {
                continue;
            }
            Map<String, String> kv = parseKeyValues(trimmed.substring("FACT:".length()).trim());
            String type = kv.get("type");
            String side = kv.get("side");
            String square = kv.get("square");
            String file = kv.get("file");
            String squares = kv.get("squares");
            if (type == null || side == null) {
                continue;
            }
            StringBuilder sb = new StringBuilder(64);
            sb.append("PAWN: structure=").append(type).append(" side=").append(side);
            if (square != null) {
                sb.append(" square=").append(square);
            }
            if (file != null) {
                sb.append(" file=").append(file);
            }
            if (squares != null) {
                sb.append(" squares=").append(squares);
            }
            tags.add(sb.toString());
        }

        PawnStats stats = PawnStats.from(position);
        tags.add("PAWN: islands side=white count=" + stats.whiteIslands);
        tags.add("PAWN: islands side=black count=" + stats.blackIslands);

        if (stats.whiteMajority != null) {
            tags.add("PAWN: majority=" + stats.whiteMajority + " side=white");
        }
        if (stats.blackMajority != null) {
            tags.add("PAWN: majority=" + stats.blackMajority + " side=black");
        }

        List<String> promotion = PromotionTagger.tags(position);
        for (String tag : promotion) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.startsWith("FACT: promotion_available")) {
                continue;
            }
            Map<String, String> kv = parseKeyValues(trimmed.substring("FACT:".length()).trim());
            String side = kv.get("side");
            String square = kv.get("square");
            if (side == null) {
                continue;
            }
            StringBuilder sb = new StringBuilder(64);
            sb.append("THREAT: type=promote side=").append(side).append(" severity=immediate");
            if (square != null) {
                sb.append(" square=").append(square);
            }
            tags.add(sb.toString());
            if ("white".equals(side)) {
                ctx.hasThreatWhite = true;
            } else if ("black".equals(side)) {
                ctx.hasThreatBlack = true;
            }
        }
    }

    private static void addKingTags(List<String> tags, TagContext ctx, Position position) {
        if (position.isChess960()) {
            return;
        }
        List<String> safety = KingSafetyTagger.tags(position);
        KingSafety white = new KingSafety();
        KingSafety black = new KingSafety();
        for (String tag : safety) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.startsWith("white")) {
                applyKingSafetyToken(white, trimmed);
            } else if (trimmed.startsWith("black")) {
                applyKingSafetyToken(black, trimmed);
            } else if (trimmed.startsWith("open file near white king")) {
                white.openFile = true;
            } else if (trimmed.startsWith("open file near black king")) {
                black.openFile = true;
            }
        }
        addKingSafetyTags(tags, "white", white);
        addKingSafetyTags(tags, "black", black);
    }

    private static void addTacticalTags(List<String> tags, TagContext ctx, Position position) {
        List<String> tactics = TacticalTagger.tags(position);
        for (String tag : tactics) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.startsWith("FACT: tactical=")) {
                continue;
            }
            String text = extractQuoted(trimmed);
            if (text == null || text.isBlank()) {
                continue;
            }
            String motif = tacticalMotif(text);
            if (motif == null) {
                continue;
            }
            String side = leadingColor(text);
            StringBuilder sb = new StringBuilder(96);
            sb.append("TACTIC: motif=").append(motif);
            if (side != null) {
                sb.append(" side=").append(side);
            }
            sb.append(" detail=\"").append(escape(text)).append("\"");
            tags.add(sb.toString());
        }
    }

    private static void addStrategicTags(List<String> tags, TagContext ctx, Position position) {
        String spaceSide = spaceSide(ctx);
        if (spaceSide != null) {
            tags.add("SPACE: side=" + spaceSide);
        }

        String developmentSide = developmentSide(position);
        if (developmentSide != null) {
            tags.add("DEVELOPMENT: side=" + developmentSide);
        }

        String mobilitySide = mobilitySide(position);
        if (mobilitySide != null) {
            tags.add("MOBILITY: side=" + mobilitySide);
        }

        String initiativeSide = initiativeSide(ctx);
        if (initiativeSide != null) {
            tags.add("INITIATIVE: side=" + initiativeSide);
        }
    }

    private static void addEndgameOpening(List<String> tags, TagContext ctx, Position position) {
        List<String> endgame = EndgameTagger.tags(position);
        for (String tag : endgame) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.startsWith("FACT: endgame=")) {
                String val = valueAfter(trimmed, "endgame=");
                if (val != null) {
                    tags.add("ENDGAME: type=" + mapEndgame(val));
                }
            } else if (trimmed.startsWith("FACT: opposite_colored_bishops=")) {
                tags.add("ENDGAME: type=opposite_bishops");
            }
        }

        List<String> opening = OpeningTagger.tags(position);
        for (String tag : opening) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.startsWith("META: eco=")) {
                String eco = valueAfter(trimmed, "eco=");
                if (eco != null) {
                    tags.add("OPENING: eco=" + eco);
                }
            } else if (trimmed.startsWith("META: opening=")) {
                String name = extractQuoted(trimmed);
                if (name != null) {
                    tags.add("OPENING: name=\"" + escape(name) + "\"");
                }
            }
        }
    }

    private static String phaseLabel(Position position) {
        int total = position.countTotalMaterial();
        double phase = Math.max(0.0, Math.min(1.0, total / (double) START_TOTAL_MATERIAL));
        if (phase >= PHASE_OPENING) {
            return "opening";
        }
        if (phase >= PHASE_MIDDLEGAME) {
            return "middlegame";
        }
        return "endgame";
    }

    private static String statusLabel(Position position) {
        if (position.isMate()) {
            return "checkmated";
        }
        MoveList moves = position.getMoves();
        if (moves.isEmpty()) {
            return position.inCheck() ? "checkmated" : "stalemate";
        }
        if (position.inCheck()) {
            return "check";
        }
        return "normal";
    }

    private static String castleRightsLabel(Position position) {
        StringBuilder sb = new StringBuilder(4);
        if (position.getWhiteKingside() != Field.NO_SQUARE) {
            sb.append('K');
        }
        if (position.getWhiteQueenside() != Field.NO_SQUARE) {
            sb.append('Q');
        }
        if (position.getBlackKingside() != Field.NO_SQUARE) {
            sb.append('k');
        }
        if (position.getBlackQueenside() != Field.NO_SQUARE) {
            sb.append('q');
        }
        if (sb.length() == 0) {
            return "none";
        }
        return sb.toString();
    }

    private static String balanceLabel(int discrepancy) {
        if (Math.abs(discrepancy) < 50) {
            return "equal";
        }
        String side = discrepancy > 0 ? "white" : "black";
        int abs = Math.abs(discrepancy);
        if (abs < 150) {
            return side + "_up_pawn";
        }
        if (abs < 350) {
            return side + "_up_minor";
        }
        if (abs < 650) {
            return side + "_up_exchange";
        }
        return side + "_up_queen";
    }

    private static String difficultyLabel(Wdl wdl) {
        if (wdl == null) {
            return null;
        }
        double expected = (wdl.win() + 0.5 * wdl.draw()) / (double) Wdl.TOTAL;
        double difficulty = logarithmicDifficulty(expected);
        if (difficulty <= 0.20) {
            return "very_easy";
        }
        if (difficulty <= 0.35) {
            return "easy";
        }
        if (difficulty <= 0.55) {
            return "medium";
        }
        if (difficulty <= 0.70) {
            return "hard";
        }
        return "very_hard";
    }

    private static double logarithmicDifficulty(double expectedScore) {
        double linear = clamp01(1.0 - expectedScore);
        double k = 3.0;
        double difficulty = Math.log1p(k * linear) / Math.log1p(k);
        return clamp01(difficulty);
    }

    private static double clamp01(double v) {
        if (v <= 0.0) {
            return 0.0;
        }
        if (v >= 1.0) {
            return 1.0;
        }
        return v;
    }

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

    private static String valueAfter(String tag, String key) {
        int idx = tag.indexOf(key);
        if (idx < 0) {
            return null;
        }
        return tag.substring(idx + key.length()).trim();
    }

    private static String extractQuoted(String tag) {
        int first = tag.indexOf('"');
        if (first < 0) {
            return null;
        }
        int second = tag.indexOf('"', first + 1);
        if (second < 0) {
            return null;
        }
        return tag.substring(first + 1, second);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Map<String, String> parseKeyValues(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return map;
        }
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            int eq = token.indexOf('=');
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

    private static ParsedPieceTier parseTierTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String lowered = tag.toLowerCase();
        String tier;
        if (lowered.startsWith("very strong")) {
            tier = "very_strong";
            lowered = lowered.substring("very strong".length()).trim();
        } else if (lowered.startsWith("strong")) {
            tier = "strong";
            lowered = lowered.substring("strong".length()).trim();
        } else if (lowered.startsWith("slightly strong")) {
            tier = "slightly_strong";
            lowered = lowered.substring("slightly strong".length()).trim();
        } else if (lowered.startsWith("neutral")) {
            tier = "neutral";
            lowered = lowered.substring("neutral".length()).trim();
        } else if (lowered.startsWith("slightly weak")) {
            tier = "slightly_weak";
            lowered = lowered.substring("slightly weak".length()).trim();
        } else if (lowered.startsWith("very weak")) {
            tier = "very_weak";
            lowered = lowered.substring("very weak".length()).trim();
        } else if (lowered.startsWith("weak")) {
            tier = "weak";
            lowered = lowered.substring("weak".length()).trim();
        } else {
            return null;
        }

        String[] parts = lowered.split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        return new ParsedPieceTier(tier, parts[0], parts[1], parts[2]);
    }

    private static ParsedPieceInfo parsePieceInfo(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        String side = parts[0];
        String piece = parts[1];
        String square = parts[2];
        return new ParsedPieceInfo(side, piece, square);
    }

    private static String tacticalMotif(String text) {
        String lowered = text.toLowerCase();
        if (lowered.startsWith("pin:")) {
            return "pin";
        }
        if (lowered.startsWith("skewer:")) {
            return "skewer";
        }
        if (lowered.startsWith("discovered attack:")) {
            return "discovered_attack";
        }
        if (lowered.startsWith("overloaded defender:")) {
            return "overload";
        }
        if (lowered.startsWith("hanging ")) {
            return "hanging";
        }
        return null;
    }

    private static String leadingColor(String text) {
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            if ("white".equals(token)) {
                return "white";
            }
            if ("black".equals(token)) {
                return "black";
            }
        }
        return null;
    }

    private static void applyKingSafetyToken(KingSafety safety, String token) {
        if (token.contains("castled")) {
            safety.castled = true;
        }
        if (token.contains("uncastled")) {
            safety.castled = false;
        }
        if (token.contains("pawn shield weakened")) {
            safety.shieldWeakened = true;
        }
        if (token.contains("king exposed")) {
            safety.exposed = true;
        }
        if (token.contains("open file near")) {
            safety.openFile = true;
        }
    }

    private static void addKingSafetyTags(List<String> tags, String side, KingSafety safety) {
        if (safety.castled != null) {
            tags.add("KING: castled=" + (safety.castled ? "yes" : "no") + " side=" + side);
        }
        String shelter = "pawns_intact";
        if (safety.openFile) {
            shelter = "open";
        } else if (safety.shieldWeakened) {
            shelter = "weakened";
        }
        tags.add("KING: shelter=" + shelter + " side=" + side);

        String safetyLabel = "safe";
        if (safety.exposed) {
            safetyLabel = "very_unsafe";
        } else if (safety.openFile || safety.shieldWeakened) {
            safetyLabel = "unsafe";
        } else if (safety.castled != null && safety.castled) {
            safetyLabel = "very_safe";
        }
        tags.add("KING: safety=" + safetyLabel + " side=" + side);
    }

    private static boolean hasOppositeColoredBishops(byte[] board) {
        boolean whiteLight = false;
        boolean whiteDark = false;
        boolean blackLight = false;
        boolean blackDark = false;
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (!Piece.isBishop(piece)) {
                continue;
            }
            int file = Field.getX((byte) index);
            int rank = Field.getY((byte) index);
            boolean light = ((file + rank) & 1) == 0;
            if (Piece.isWhite(piece)) {
                if (light) {
                    whiteLight = true;
                } else {
                    whiteDark = true;
                }
            } else {
                if (light) {
                    blackLight = true;
                } else {
                    blackDark = true;
                }
            }
        }
        return (whiteLight && blackDark) || (whiteDark && blackLight);
    }

    private static boolean isInsufficientMaterial(Position position) {
        byte[] board = position.getBoard();
        int whiteKnights = 0;
        int whiteBishops = 0;
        int blackKnights = 0;
        int blackBishops = 0;
        for (byte piece : board) {
            if (piece == Piece.EMPTY || Piece.isKing(piece)) {
                continue;
            }
            if (Piece.isPawn(piece) || Piece.isQueen(piece) || Piece.isRook(piece)) {
                return false;
            }
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
        int whiteMinors = whiteKnights + whiteBishops;
        int blackMinors = blackKnights + blackBishops;
        if (whiteMinors == 0 && blackMinors == 0) {
            return true;
        }
        if (whiteMinors == 1 && blackMinors == 0) {
            return true;
        }
        if (whiteMinors == 0 && blackMinors == 1) {
            return true;
        }
        if (whiteMinors == 1 && blackMinors == 1) {
            return true;
        }
        if (whiteKnights == 2 && whiteBishops == 0 && blackMinors == 0) {
            return true;
        }
        if (blackKnights == 2 && blackBishops == 0 && whiteMinors == 0) {
            return true;
        }
        return false;
    }

    private static String mapEndgame(String val) {
        switch (val) {
            case "queenless":
                return "queenless";
            case "rook_endgame":
                return "rook";
            case "minor_piece_endgame":
                return "minor";
            default:
                return val;
        }
    }

    private static String spaceSide(TagContext ctx) {
        if (ctx.spaceAdvantage != null) {
            if ("white".equals(ctx.spaceAdvantage) || "black".equals(ctx.spaceAdvantage)) {
                return ctx.spaceAdvantage;
            }
        }
        if (ctx.centerControl != null) {
            if ("white".equals(ctx.centerControl)) {
                return "white";
            }
            if ("black".equals(ctx.centerControl)) {
                return "black";
            }
        }
        return "equal";
    }

    private static String developmentSide(Position position) {
        int white = undevelopedMinors(position, true);
        int black = undevelopedMinors(position, false);
        int diff = black - white;
        if (diff >= 2) {
            return "white";
        }
        if (diff <= -2) {
            return "black";
        }
        return "equal";
    }

    private static String mobilitySide(Position position) {
        int whiteMoves = mobilityForSide(position, true);
        int blackMoves = mobilityForSide(position, false);
        int diff = whiteMoves - blackMoves;
        if (diff >= 5) {
            return "white";
        }
        if (diff <= -5) {
            return "black";
        }
        return "equal";
    }

    private static String initiativeSide(TagContext ctx) {
        if (ctx.hasThreatWhite && !ctx.hasThreatBlack) {
            return "white";
        }
        if (ctx.hasThreatBlack && !ctx.hasThreatWhite) {
            return "black";
        }
        if (ctx.evalCpWhite != null) {
            if (ctx.evalCpWhite > 80) {
                return "white";
            }
            if (ctx.evalCpWhite < -80) {
                return "black";
            }
        }
        return "equal";
    }

    private static int undevelopedMinors(Position position, boolean white) {
        int count = 0;
        byte[] board = position.getBoard();
        if (white) {
            count += isPieceAt(board, Piece.WHITE_KNIGHT, Field.B1) ? 1 : 0;
            count += isPieceAt(board, Piece.WHITE_KNIGHT, Field.G1) ? 1 : 0;
            count += isPieceAt(board, Piece.WHITE_BISHOP, Field.C1) ? 1 : 0;
            count += isPieceAt(board, Piece.WHITE_BISHOP, Field.F1) ? 1 : 0;
        } else {
            count += isPieceAt(board, Piece.BLACK_KNIGHT, Field.B8) ? 1 : 0;
            count += isPieceAt(board, Piece.BLACK_KNIGHT, Field.G8) ? 1 : 0;
            count += isPieceAt(board, Piece.BLACK_BISHOP, Field.C8) ? 1 : 0;
            count += isPieceAt(board, Piece.BLACK_BISHOP, Field.F8) ? 1 : 0;
        }
        return count;
    }

    private static boolean isPieceAt(byte[] board, byte piece, byte square) {
        if (square == Field.NO_SQUARE) {
            return false;
        }
        return board[square] == piece;
    }

    private static int mobilityForSide(Position position, boolean white) {
        if (position.isWhiteTurn() == white) {
            return position.getMoves().size();
        }
        String fen = position.toString();
        String[] parts = fen.split(" ");
        if (parts.length < 2) {
            return position.getMoves().size();
        }
        parts[1] = white ? "w" : "b";
        String flipped = String.join(" ", parts);
        try {
            Position other = new Position(flipped);
            return other.getMoves().size();
        } catch (IllegalArgumentException ex) {
            return position.getMoves().size();
        }
    }

    private static final class ParsedPieceTier {
        private final String tier;
        private final String side;
        private final String piece;
        private final String square;

        private ParsedPieceTier(String tier, String side, String piece, String square) {
            this.tier = tier;
            this.side = side;
            this.piece = piece;
            this.square = square;
        }
    }

    private static final class ParsedPieceInfo {
        private final String side;
        private final String piece;
        private final String square;

        private ParsedPieceInfo(String side, String piece, String square) {
            this.side = side;
            this.piece = piece;
            this.square = square;
        }
    }

    private static final class KingSafety {
        private Boolean castled;
        private boolean shieldWeakened;
        private boolean exposed;
        private boolean openFile;
    }

    private static final class PawnStats {
        private final int whiteIslands;
        private final int blackIslands;
        private final String whiteMajority;
        private final String blackMajority;

        private PawnStats(int whiteIslands, int blackIslands, String whiteMajority, String blackMajority) {
            this.whiteIslands = whiteIslands;
            this.blackIslands = blackIslands;
            this.whiteMajority = whiteMajority;
            this.blackMajority = blackMajority;
        }

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

        private static String majoritySide(int[] whiteFiles, int[] blackFiles, boolean forWhite) {
            int[] regions = new int[] { 0, 3, 5, 8 }; // a-c, d-e, f-h
            String[] labels = new String[] { "queenside", "center", "kingside" };
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
                if (forWhite && diff >= 2) {
                    best = labels[i];
                } else if (!forWhite && diff <= -2) {
                    best = labels[i];
                }
            }
            return best;
        }
    }

    private static final class TagContext {
        private final boolean whiteToMove;
        private Integer evalCpStm;
        private Integer evalCpWhite;
        private Wdl wdl;
        private String centerControl;
        private String spaceAdvantage;
        private boolean hasThreatWhite;
        private boolean hasThreatBlack;

        private TagContext(Position position) {
            this.whiteToMove = position.isWhiteTurn();
        }
    }
}
