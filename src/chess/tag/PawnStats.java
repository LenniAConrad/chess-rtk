package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Stores pawn-file and pawn-majority statistics for both sides.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
final class PawnStats {

    /**
     * The number of White pawn islands.
     */
    final int whiteIslands;

    /**
     * The number of Black pawn islands.
     */
    final int blackIslands;

    /**
     * White's majority-side label, if any.
     */
    final String whiteMajority;

    /**
     * Black's majority-side label, if any.
     */
    final String blackMajority;

    /**
     * Creates a pawn-statistics snapshot.
     *
     * @param whiteIslands  the White pawn-island count
     * @param blackIslands  the Black pawn-island count
     * @param whiteMajority the White majority label
     * @param blackMajority the Black majority label
     */
    PawnStats(int whiteIslands, int blackIslands, String whiteMajority, String blackMajority) {
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
    static PawnStats from(Position position) {
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
     * @param forWhite   whether the query is for White's perspective
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
