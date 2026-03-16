package application.cli.command;

import java.util.ArrayList;
import java.util.List;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.struct.Record;
import chess.uci.Analysis;
import chess.uci.Output;

/**
 * Helpers for building puzzle line records (main PV + variations).
 */
final class PuzzleSupport {

    private PuzzleSupport() {
        // utility
    }

    static List<Record> buildRecords(Position root, Analysis analysis, int pvPlies, String cmd, boolean verbose) {
        if (analysis == null || analysis.isEmpty()) {
            System.err.println(cmd + ": analysis unavailable for puzzle line");
            System.exit(2);
        }
        List<List<String>> pvs = extractPvSanLines(root, analysis, pvPlies);
        if (pvs.isEmpty()) {
            System.err.println(cmd + ": no PVs found for puzzle line");
            System.exit(2);
        }
        String pgn = buildPgn(root, pvs);
        Game game = parseSingleGame(pgn, cmd, verbose);
        return application.cli.PgnOps.extractRecordsWithVariations(game);
    }

    private static List<List<String>> extractPvSanLines(Position root, Analysis analysis, int pvPlies) {
        int pivots = Math.max(1, analysis.getPivots());
        List<List<String>> lines = new ArrayList<>(pivots);
        for (int pv = 1; pv <= pivots; pv++) {
            Output output = analysis.getBestOutput(pv);
            if (output == null) {
                continue;
            }
            short[] moves = output.getMoves();
            if (moves == null || moves.length == 0) {
                continue;
            }
            List<String> sanMoves = toSanMoves(root, moves, pvPlies);
            if (!sanMoves.isEmpty()) {
                lines.add(sanMoves);
            }
        }
        return lines;
    }

    private static List<String> toSanMoves(Position root, short[] moves, int pvPlies) {
        int limit = pvPlies > 0 ? Math.min(pvPlies, moves.length) : moves.length;
        List<String> sanMoves = new ArrayList<>(limit);
        Position cursor = root.copyOf();
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
            try {
                cursor.play(move);
            } catch (RuntimeException ex) {
                break;
            }
        }
        return sanMoves;
    }

    private static String buildPgn(Position root, List<List<String>> pvs) {
        if (pvs.isEmpty() || pvs.get(0).isEmpty()) {
            throw new IllegalArgumentException("Missing PV1 for puzzle PGN");
        }
        boolean whiteToMove = root.isWhiteTurn();
        int fullMove = root.getFullMove();

        List<String> variations = new ArrayList<>();
        for (int i = 1; i < pvs.size(); i++) {
            List<String> alt = pvs.get(i);
            if (alt == null || alt.isEmpty()) {
                continue;
            }
            variations.add("(" + formatLine(alt, whiteToMove, fullMove, null) + ")");
        }

        String mainLine = formatLine(pvs.get(0), whiteToMove, fullMove, variations);

        StringBuilder sb = new StringBuilder(512);
        sb.append("[Event \"Puzzle PVs\"]\n");
        sb.append("[Site \"?\"]\n");
        sb.append("[Date \"????.??.??\"]\n");
        sb.append("[Round \"?\"]\n");
        sb.append("[White \"?\"]\n");
        sb.append("[Black \"?\"]\n");
        sb.append("[SetUp \"1\"]\n");
        sb.append("[FEN \"").append(root.toString()).append("\"]\n\n");
        sb.append(mainLine).append(" *\n");
        return sb.toString();
    }

    private static String formatLine(List<String> moves, boolean whiteToMove, int fullMove, List<String> variations) {
        if (moves == null || moves.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int moveNo = Math.max(1, fullMove);
        boolean white = whiteToMove;
        for (int i = 0; i < moves.size(); i++) {
            String san = moves.get(i);
            if (white) {
                sb.append(moveNo).append(". ").append(san);
            } else {
                sb.append(moveNo).append("... ").append(san);
                moveNo++;
            }
            if (i == 0 && variations != null && !variations.isEmpty()) {
                for (String var : variations) {
                    sb.append(' ').append(var);
                }
            }
            white = !white;
            if (i + 1 < moves.size()) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private static Game parseSingleGame(String pgn, String cmd, boolean verbose) {
        List<Game> games = Pgn.parseGames(pgn);
        if (games.isEmpty()) {
            System.err.println(cmd + ": failed to parse generated PGN");
            System.exit(2);
        }
        return games.get(0);
    }
}
