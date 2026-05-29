package chess.describe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import chess.classical.Wdl;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.tag.material.Counts;
import chess.tag.pawn.Structure;
import chess.tag.position.CenterSpace;
import chess.tag.position.KingSafety;

/**
 * Extracts cheap deterministic position-description signals.
 */
public final class PositionDescriptionExtractor {

    /**
     * Approximate starting material total used for phase bucketing.
     */
    private static final int START_TOTAL_MATERIAL = 8000;

    /**
     * Minimum remaining-material ratio treated as opening.
     */
    private static final double PHASE_OPENING = 0.75;

    /**
     * Minimum remaining-material ratio treated as middlegame.
     */
    private static final double PHASE_MIDDLEGAME = 0.35;

    /**
     * Maximum number of deterministic candidate moves retained.
     */
    private static final int DEFAULT_CANDIDATE_LIMIT = 5;

    /**
     * Prevents instantiation.
     */
    private PositionDescriptionExtractor() {
        // utility
    }

    /**
     * Extracts the shared input from a position.
     *
     * @param position position
     * @return description input
     */
    public static PositionDescriptionInput extract(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }
        MoveList legal = position.legalMoves();
        MoveScan scan = scanMoves(position, legal);
        String side = position.isWhiteToMove() ? "white" : "black";
        String status = status(position, legal);
        String phase = phase(position);
        PositionDescriptionInput.Material material = material(position);
        int cpWhite = Wdl.evaluateWhiteCentipawns(position);
        int cpSide = position.isWhiteToMove() ? cpWhite : -cpWhite;
        PositionDescriptionInput.Evaluation evaluation =
                new PositionDescriptionInput.Evaluation("classical-static", cpWhite, cpSide, Wdl.evaluate(position));
        List<String> tags = cheapTags(position, side, status, phase, material, scan.summary(), evaluation);
        List<String> threats = threats(position, scan.summary());
        return new PositionDescriptionInput(
                position.toString(),
                side,
                status,
                position.inCheck(),
                phase,
                material,
                scan.summary(),
                evaluation,
                tags,
                threats,
                scan.candidates());
    }

    /**
     * Extracts per-side material counts and balance.
     *
     * @param position source position
     * @return material summary
     */
    private static PositionDescriptionInput.Material material(Position position) {
        return new PositionDescriptionInput.Material(
                position.countWhiteMaterial(),
                position.countBlackMaterial(),
                position.materialDiscrepancy(),
                new PositionDescriptionInput.PieceCounts(
                        position.countPieces(Piece.WHITE_KING),
                        position.countPieces(Piece.WHITE_QUEEN),
                        position.countPieces(Piece.WHITE_ROOK),
                        position.countPieces(Piece.WHITE_BISHOP),
                        position.countPieces(Piece.WHITE_KNIGHT),
                        position.countPieces(Piece.WHITE_PAWN)),
                new PositionDescriptionInput.PieceCounts(
                        position.countPieces(Piece.BLACK_KING),
                        position.countPieces(Piece.BLACK_QUEEN),
                        position.countPieces(Piece.BLACK_ROOK),
                        position.countPieces(Piece.BLACK_BISHOP),
                        position.countPieces(Piece.BLACK_KNIGHT),
                        position.countPieces(Piece.BLACK_PAWN)));
    }

    /**
     * Scans legal moves for move-category counts and candidate ordering.
     *
     * @param position source position
     * @param legal legal move list
     * @return move scan summary
     */
    private static MoveScan scanMoves(Position position, MoveList legal) {
        int captures = 0;
        int checks = 0;
        int mates = 0;
        int promotions = 0;
        int castles = 0;
        int enPassant = 0;
        int quiet = 0;
        int forcing = 0;
        List<PositionDescriptionInput.CandidateMove> candidates = new ArrayList<>();
        Position.State undo = new Position.State();
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.raw(i);
            boolean capture = position.isCapture(move);
            boolean promotion = Move.isPromotion(move);
            boolean castle = position.isCastle(move);
            boolean ep = position.isEnPassantCapture(move);
            boolean givesCheck;
            boolean mate;
            position.play(move, undo);
            try {
                givesCheck = position.inCheck();
                mate = givesCheck && position.legalMoves().isEmpty();
            } finally {
                position.undo(move, undo);
            }
            captures += capture ? 1 : 0;
            checks += givesCheck ? 1 : 0;
            mates += mate ? 1 : 0;
            promotions += promotion ? 1 : 0;
            castles += castle ? 1 : 0;
            enPassant += ep ? 1 : 0;
            boolean isQuiet = !capture && !givesCheck && !promotion && !castle;
            quiet += isQuiet ? 1 : 0;
            forcing += capture || givesCheck || promotion ? 1 : 0;
            candidates.add(candidate(position, move, capture, promotion, castle, ep, givesCheck, mate));
        }
        candidates.sort(Comparator
                .comparingInt(PositionDescriptionInput.CandidateMove::priority).reversed()
                .thenComparing(PositionDescriptionInput.CandidateMove::uci));
        if (candidates.size() > DEFAULT_CANDIDATE_LIMIT) {
            candidates = new ArrayList<>(candidates.subList(0, DEFAULT_CANDIDATE_LIMIT));
        }
        PositionDescriptionInput.MoveSummary summary = new PositionDescriptionInput.MoveSummary(
                legal.size(), captures, checks, mates, promotions, castles, enPassant, quiet, forcing);
        return new MoveScan(summary, List.copyOf(candidates));
    }

    /**
     * Builds one candidate-move summary.
     *
     * @param position source position
     * @param move encoded move
     * @param capture true when the move captures
     * @param promotion true when the move promotes
     * @param castle true when the move castles
     * @param enPassant true when the move captures en passant
     * @param check true when the move gives check
     * @param mate true when the move gives mate
     * @return candidate move
     */
    private static PositionDescriptionInput.CandidateMove candidate(
            Position position,
            short move,
            boolean capture,
            boolean promotion,
            boolean castle,
            boolean enPassant,
            boolean check,
            boolean mate) {
        String uci = Move.toString(move);
        String san = safeSan(position, move);
        String reason = reason(position, move, capture, promotion, castle, enPassant, check, mate);
        int priority = priority(position, move, capture, promotion, castle, enPassant, check, mate);
        return new PositionDescriptionInput.CandidateMove(uci, san, reason, priority);
    }

    /**
     * Converts a move to SAN, falling back to UCI on unexpected conversion failure.
     *
     * @param position source position
     * @param move encoded move
     * @return SAN or UCI text
     */
    private static String safeSan(Position position, short move) {
        try {
            return SAN.toAlgebraic(position, move);
        } catch (RuntimeException ex) {
            return Move.toString(move);
        }
    }

    /**
     * Scores a candidate move for deterministic display ordering.
     *
     * @param position source position
     * @param move encoded move
     * @param capture true when the move captures
     * @param promotion true when the move promotes
     * @param castle true when the move castles
     * @param enPassant true when the move captures en passant
     * @param check true when the move gives check
     * @param mate true when the move gives mate
     * @return priority score
     */
    private static int priority(
            Position position,
            short move,
            boolean capture,
            boolean promotion,
            boolean castle,
            boolean enPassant,
            boolean check,
            boolean mate) {
        int score = 0;
        if (mate) {
            score += 100_000;
        }
        if (check) {
            score += 9_000;
        }
        if (promotion) {
            score += 18_000 + promotionValue(move);
        }
        if (capture) {
            score += 10_000 + captureValue(position, move, enPassant);
        }
        if (castle) {
            score += 1_200;
        }
        score += quietMoveScore(position, move);
        return score;
    }

    /**
     * Returns the captured piece value for capture ordering.
     *
     * @param position source position
     * @param move encoded move
     * @param enPassant true when the capture is en passant
     * @return captured piece value
     */
    private static int captureValue(Position position, short move, boolean enPassant) {
        if (enPassant) {
            return Piece.VALUE_PAWN;
        }
        byte target = position.pieceAt(position.actualToSquare(move));
        return Piece.getValue(target);
    }

    /**
     * Returns the material value of a promotion piece.
     *
     * @param move encoded promotion move
     * @return promotion piece value
     */
    private static int promotionValue(short move) {
        return switch (Move.getPromotion(move)) {
            case 1 -> Piece.VALUE_KNIGHT;
            case 2 -> Piece.VALUE_BISHOP;
            case 3 -> Piece.VALUE_ROOK;
            case 4 -> Piece.VALUE_QUEEN;
            default -> 0;
        };
    }

    /**
     * Scores non-forcing development and space-gaining moves.
     *
     * @param position source position
     * @param move encoded move
     * @return quiet-move score
     */
    private static int quietMoveScore(Position position, short move) {
        byte from = Move.getFromIndex(move);
        byte to = position.actualToSquare(move);
        if (from == Field.NO_SQUARE || to == Field.NO_SQUARE) {
            return 0;
        }
        byte moving = position.pieceAt(from);
        int score = centrality(to) - centrality(from);
        if (Piece.isKnight(moving) || Piece.isBishop(moving)) {
            score *= 24;
            if (isBackRank(from, Piece.isWhite(moving)) && !isEdgeFile(to)) {
                score += 900;
            }
        } else if (Piece.isPawn(moving)) {
            int file = Field.getX(to);
            int relativeRank = Piece.isWhite(moving) ? Field.getY(to) : 7 - Field.getY(to);
            score += Math.max(0, relativeRank - 1) * 80;
            if ((file == 3 || file == 4) && Math.abs(Field.getY(to) - Field.getY(from)) == 2) {
                score += 760;
            } else if (file == 3 || file == 4) {
                score += 520;
            }
        } else if (Piece.isRook(moving) || Piece.isQueen(moving)) {
            score *= 10;
        }
        return score;
    }

    /**
     * Selects the short human-readable reason attached to a candidate move.
     *
     * @param position source position
     * @param move encoded move
     * @param capture true when the move captures
     * @param promotion true when the move promotes
     * @param castle true when the move castles
     * @param enPassant true when the move captures en passant
     * @param check true when the move gives check
     * @param mate true when the move gives mate
     * @return reason label
     */
    private static String reason(
            Position position,
            short move,
            boolean capture,
            boolean promotion,
            boolean castle,
            boolean enPassant,
            boolean check,
            boolean mate) {
        if (mate) {
            return "mates";
        }
        if (promotion) {
            return "promotes";
        }
        if (check) {
            return "gives check";
        }
        if (capture) {
            return enPassant ? "captures en passant" : "captures";
        }
        if (castle) {
            return "castles";
        }
        byte moving = position.pieceAt(Move.getFromIndex(move));
        if (Piece.isKnight(moving) || Piece.isBishop(moving)) {
            return "develops a minor piece";
        }
        if (Piece.isPawn(moving)) {
            int file = Field.getX(position.actualToSquare(move));
            if (file == 3 || file == 4) {
                return "takes central space";
            }
            return "advances a pawn";
        }
        return "improves a piece";
    }

    /**
     * Scores a square by closeness to the board center.
     *
     * @param square square index
     * @return centrality score
     */
    private static int centrality(int square) {
        int file = Field.getX((byte) square);
        int rank = Field.getY((byte) square);
        return 14 - 2 * (Math.abs(2 * file - 7) + Math.abs(2 * rank - 7));
    }

    /**
     * Tests whether a square is on the home rank for the given color.
     *
     * @param square square index
     * @param white true for White
     * @return true when the square is on the back rank
     */
    private static boolean isBackRank(byte square, boolean white) {
        int rank = Field.getY(square);
        return white ? rank == 0 : rank == 7;
    }

    /**
     * Tests whether a square is on file a or h.
     *
     * @param square square index
     * @return true when the square is on an edge file
     */
    private static boolean isEdgeFile(byte square) {
        int file = Field.getX(square);
        return file == 0 || file == 7;
    }

    /**
     * Collects deterministic source tags from the cheap tagging modules.
     *
     * @param position source position
     * @param side side-to-move label
     * @param status status label
     * @param phase phase label
     * @param material material summary
     * @param moves move summary
     * @param evaluation evaluation summary
     * @return sorted source tags
     */
    private static List<String> cheapTags(
            Position position,
            String side,
            String status,
            String phase,
            PositionDescriptionInput.Material material,
            PositionDescriptionInput.MoveSummary moves,
            PositionDescriptionInput.Evaluation evaluation) {
        TreeSet<String> out = new TreeSet<>();
        out.add("META: to_move=" + side);
        out.add("META: phase=" + phase);
        out.add("META: eval_cp_white=" + evaluation.cpWhite());
        out.add("FACT: status=" + status);
        out.add("MOVE: legal=" + moves.legal());
        out.add("MOVE: forcing=" + moves.forcing());
        out.add("MATERIAL: balance_cp=" + material.balanceCp());
        out.addAll(Counts.tags(position));
        out.addAll(CenterSpace.tags(position));
        out.addAll(Structure.tags(position));
        out.addAll(KingSafety.tags(position));
        return List.copyOf(out);
    }

    /**
     * Builds immediate tactical signal labels from check and move counts.
     *
     * @param position source position
     * @param moves move summary
     * @return threat labels
     */
    private static List<String> threats(Position position, PositionDescriptionInput.MoveSummary moves) {
        List<String> out = new ArrayList<>();
        if (position.inCheck()) {
            out.add("side to move is in check");
        }
        if (moves.mates() > 0) {
            out.add(moves.mates() == 1 ? "mate in one available" : moves.mates() + " mate-in-one moves available");
        }
        if (moves.promotions() > 0) {
            out.add(moves.promotions() == 1 ? "promotion available" : moves.promotions() + " promotions available");
        }
        if (moves.checks() > 0) {
            out.add(moves.checks() == 1 ? "one checking move" : moves.checks() + " checking moves");
        }
        if (moves.captures() > 0) {
            out.add(moves.captures() == 1 ? "one capture" : moves.captures() + " captures");
        }
        return List.copyOf(out);
    }

    /**
     * Classifies the game status relevant to a text description.
     *
     * @param position source position
     * @param legal legal move list
     * @return status label
     */
    private static String status(Position position, MoveList legal) {
        if (legal.isEmpty()) {
            return position.inCheck() ? "checkmate" : "stalemate";
        }
        if (position.inCheck()) {
            return "check";
        }
        if (position.isInsufficientMaterial()) {
            return "insufficient material";
        }
        return "normal";
    }

    /**
     * Buckets the position into opening, middlegame, or endgame by material.
     *
     * @param position source position
     * @return phase label
     */
    private static String phase(Position position) {
        double factor = Math.max(0.0, Math.min(1.0, position.countTotalMaterial() / (double) START_TOTAL_MATERIAL));
        if (factor >= PHASE_OPENING) {
            return "opening";
        }
        if (factor >= PHASE_MIDDLEGAME) {
            return "middlegame";
        }
        return "endgame";
    }

    /**
     * Combined legal-move scan result.
     *
     * @param summary move summary
     * @param candidates ordered candidate moves
     */
    private record MoveScan(
            PositionDescriptionInput.MoveSummary summary,
            List<PositionDescriptionInput.CandidateMove> candidates) {
    }
}
