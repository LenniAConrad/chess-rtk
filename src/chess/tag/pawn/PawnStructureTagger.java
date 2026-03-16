package chess.tag.pawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.tag.core.Text;

/**
 * Emits pawn-structure related tags.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PawnStructureTagger {

    private PawnStructureTagger() {
        // utility
    }

    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");
        byte[] board = position.getBoard();
        List<Byte> whitePawns = new ArrayList<>();
        List<Byte> blackPawns = new ArrayList<>();
        int[] whiteFileCounts = new int[8];
        int[] blackFileCounts = new int[8];

        for (int i = 0; i < board.length; i++) {
            byte piece = board[i];
            if (!Piece.isPawn(piece)) {
                continue;
            }
            byte square = (byte) i;
            int file = Field.getX(square);
            if (Piece.isWhite(piece)) {
                whitePawns.add(square);
                whiteFileCounts[file]++;
            } else {
                blackPawns.add(square);
                blackFileCounts[file]++;
            }
        }

        List<String> tags = new ArrayList<>();
        addDoubled(tags, true, whiteFileCounts);
        addDoubled(tags, false, blackFileCounts);

        List<Byte> whitePassed = new ArrayList<>();
        List<Byte> blackPassed = new ArrayList<>();

        for (byte square : whitePawns) {
            boolean isolated = isIsolatedPawn(square, whitePawns);
            if (isolated) {
                tags.add(formatPawnTag("isolated", true, square));
            }
            boolean passed = isPassedPawn(square, true, blackPawns);
            if (passed) {
                tags.add(formatPawnTag("passed", true, square));
                whitePassed.add(square);
            }
            if (!isolated && isBackwardPawn(square, true, whitePawns, blackPawns)) {
                tags.add(formatPawnTag("backward", true, square));
            }
        }

        for (byte square : blackPawns) {
            boolean isolated = isIsolatedPawn(square, blackPawns);
            if (isolated) {
                tags.add(formatPawnTag("isolated", false, square));
            }
            boolean passed = isPassedPawn(square, false, whitePawns);
            if (passed) {
                tags.add(formatPawnTag("passed", false, square));
                blackPassed.add(square);
            }
            if (!isolated && isBackwardPawn(square, false, blackPawns, whitePawns)) {
                tags.add(formatPawnTag("backward", false, square));
            }
        }

        addConnectedPassers(tags, true, whitePassed);
        addConnectedPassers(tags, false, blackPassed);

        return List.copyOf(tags);
    }

    private static void addDoubled(List<String> tags, boolean white, int[] fileCounts) {
        for (int file = 0; file < fileCounts.length; file++) {
            if (fileCounts[file] > 1) {
                char fileChar = (char) ('a' + file);
                tags.add("FACT: pawn_structure type=doubled side=" + (white ? "white" : "black") + " file=" + fileChar);
            }
        }
    }

    private static boolean isIsolatedPawn(byte square, List<Byte> pawns) {
        int file = Field.getX(square);
        for (byte other : pawns) {
            int otherFile = Field.getX(other);
            if (otherFile == file - 1 || otherFile == file + 1) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPassedPawn(byte square, boolean white, List<Byte> enemyPawns) {
        int file = Field.getX(square);
        int rank = Field.getY(square);
        for (byte enemy : enemyPawns) {
            int enemyFile = Field.getX(enemy);
            if (Math.abs(enemyFile - file) > 1) {
                continue;
            }
            int enemyRank = Field.getY(enemy);
            if (white && enemyRank > rank) {
                return false;
            }
            if (!white && enemyRank < rank) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBackwardPawn(byte square, boolean white, List<Byte> friendlyPawns, List<Byte> enemyPawns) {
        int file = Field.getX(square);
        int rank = Field.getY(square);
        int forwardRank = white ? rank + 1 : rank - 1;
        if (forwardRank < 0 || forwardRank > 7) {
            return false;
        }
        int maxAdj = white ? -1 : 8;
        boolean hasAdj = false;
        for (byte pawn : friendlyPawns) {
            int pawnFile = Field.getX(pawn);
            if (Math.abs(pawnFile - file) != 1) {
                continue;
            }
            hasAdj = true;
            int pawnRank = Field.getY(pawn);
            if (white) {
                if (pawnRank > maxAdj) {
                    maxAdj = pawnRank;
                }
            } else {
                if (pawnRank < maxAdj) {
                    maxAdj = pawnRank;
                }
            }
        }
        if (!hasAdj) {
            return false;
        }
        if (white && maxAdj <= rank) {
            return false;
        }
        if (!white && maxAdj >= rank) {
            return false;
        }
        if (isPassedPawn(square, white, enemyPawns)) {
            return false;
        }
        int targetRank = forwardRank;
        int targetFile = file;
        for (byte enemy : enemyPawns) {
            int enemyRank = Field.getY(enemy);
            int enemyFile = Field.getX(enemy);
            if (white && enemyRank == targetRank + 1 && Math.abs(enemyFile - targetFile) == 1) {
                return true;
            }
            if (!white && enemyRank == targetRank - 1 && Math.abs(enemyFile - targetFile) == 1) {
                return true;
            }
        }
        return false;
    }

    private static void addConnectedPassers(List<String> tags, boolean white, List<Byte> passed) {
        passed.sort((a, b) -> Byte.compare(a, b));
        for (int i = 0; i < passed.size(); i++) {
            byte a = passed.get(i);
            int fileA = Field.getX(a);
            for (int j = i + 1; j < passed.size(); j++) {
                byte b = passed.get(j);
                int fileB = Field.getX(b);
                if (fileB - fileA > 1) {
                    break;
                }
                if (fileB == fileA + 1) {
                    tags.add("FACT: pawn_structure type=connected_passed side=" + (white ? "white" : "black")
                            + " squares=" + Text.squareNameLower(a) + "," + Text.squareNameLower(b));
                }
            }
        }
    }

    private static String formatPawnTag(String label, boolean white, byte square) {
        return "FACT: pawn_structure type=" + label + " side=" + (white ? "white" : "black") + " square="
                + Text.squareNameLower(square);
    }
}
