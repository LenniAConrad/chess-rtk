package chess.tag.pawn;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.tag.core.Text;

/**
 * Derives structural pawn tags from the current position.
 * <p>
 * The generated tags cover doubled pawns, isolated pawns, backward pawns,
 * passed pawns, and connected passed pawns.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class PawnStructure {

    /**
     * Prevents instantiation of this utility class.
     */
    private PawnStructure() {
        // utility
    }

    /**
     * Returns the canonical pawn-structure tags for the given position.
     *
     * @param position the position to inspect
     * @return an immutable list of pawn-structure facts
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, POSITION);
        byte[] board = position.getBoard();
        PawnCollection pawns = collectPawns(board);

        List<String> tags = new ArrayList<>();
        addDoubled(tags, true, pawns.whiteFileCounts);
        addDoubled(tags, false, pawns.blackFileCounts);

        List<Byte> whitePassed = new ArrayList<>();
        List<Byte> blackPassed = new ArrayList<>();
        addSidePawnTags(tags, true, pawns.whitePawns, pawns.blackPawns, whitePassed);
        addSidePawnTags(tags, false, pawns.blackPawns, pawns.whitePawns, blackPassed);

        addConnectedPassers(tags, true, whitePassed);
        addConnectedPassers(tags, false, blackPassed);

        return List.copyOf(tags);
    }

    /**
     * Collects all pawns and file counts from the board.
     *
     * @param board the board array to inspect
     * @return a snapshot of pawn locations and file counts
     */
    private static PawnCollection collectPawns(byte[] board) {
        List<Byte> whitePawns = new ArrayList<>();
        List<Byte> blackPawns = new ArrayList<>();
        int[] whiteFileCounts = new int[8];
        int[] blackFileCounts = new int[8];
        for (int i = 0; i < board.length; i++) {
            byte piece = board[i];
            if (Piece.isPawn(piece)) {
                addPawn(Piece.isWhite(piece), (byte) i, whitePawns, blackPawns, whiteFileCounts, blackFileCounts);
            }
        }
        return new PawnCollection(whitePawns, blackPawns, whiteFileCounts, blackFileCounts);
    }

    /**
     * Adds a pawn to the relevant side collection and file counter.
     *
     * @param white whether the pawn belongs to White
     * @param square the pawn's square
     * @param whitePawns the mutable White pawn list
     * @param blackPawns the mutable Black pawn list
     * @param whiteFileCounts the mutable White file counters
     * @param blackFileCounts the mutable Black file counters
     */
    private static void addPawn(boolean white, byte square, List<Byte> whitePawns, List<Byte> blackPawns,
            int[] whiteFileCounts, int[] blackFileCounts) {
        int file = Field.getX(square);
        if (white) {
            whitePawns.add(square);
            whiteFileCounts[file]++;
        } else {
            blackPawns.add(square);
            blackFileCounts[file]++;
        }
    }

    /**
     * Adds isolated, passed, and backward pawn tags for one side.
     *
     * @param tags the mutable tag accumulator
     * @param white whether the side being processed is White
     * @param friendlyPawns the pawns belonging to the side being processed
     * @param enemyPawns the opposing side's pawns
     * @param passedPawns the list that collects passed pawns for later processing
     */
    private static void addSidePawnTags(List<String> tags, boolean white, List<Byte> friendlyPawns,
            List<Byte> enemyPawns, List<Byte> passedPawns) {
        for (byte square : friendlyPawns) {
            boolean isolated = isIsolatedPawn(square, friendlyPawns);
            addPawnTagIf(tags, isolated, ISOLATED, white, square);
            boolean passed = isPassedPawn(square, white, enemyPawns);
            if (passed) {
                tags.add(formatPawnTag(PASSED, white, square));
                passedPawns.add(square);
            }
            addPawnTagIf(tags, !isolated && isBackwardPawn(square, white, friendlyPawns, enemyPawns),
                    BACKWARD, white, square);
        }
    }

    /**
     * Adds a pawn tag when a condition is met.
     *
     * @param tags the mutable tag accumulator
     * @param condition whether the tag should be emitted
     * @param label the pawn-structure label to emit
     * @param white whether the tag belongs to White
     * @param square the pawn square being described
     */
    private static void addPawnTagIf(List<String> tags, boolean condition, String label, boolean white, byte square) {
        if (condition) {
            tags.add(formatPawnTag(label, white, square));
        }
    }

    /**
     * Adds doubled-pawn tags for any file containing more than one pawn.
     *
     * @param tags the mutable tag accumulator
     * @param white whether the side being processed is White
     * @param fileCounts the pawn counts per file
     */
    private static void addDoubled(List<String> tags, boolean white, int[] fileCounts) {
        for (int file = 0; file < fileCounts.length; file++) {
            if (fileCounts[file] > 1) {
                char fileChar = (char) (FILE_A + file);
                tags.add(FACT_PAWN_STRUCTURE_PREFIX + DOUBLED + SIDE_FIELD + (white ? WHITE : BLACK) + FILE_FIELD
                        + fileChar);
            }
        }
    }

    /**
     * Checks whether a pawn has no friendly pawns on adjacent files.
     *
     * @param square the pawn square to inspect
     * @param pawns the friendly pawn list
     * @return {@code true} when the pawn is isolated
     */
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

    /**
     * Checks whether a pawn is passed against the enemy pawn structure.
     *
     * @param square the pawn square to inspect
     * @param white whether the pawn belongs to White
     * @param enemyPawns the enemy pawn list
     * @return {@code true} when no enemy pawn blocks or contests the pawn's path
     */
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

    /**
     * Checks whether a pawn is backward relative to its support and enemy blockers.
     *
     * @param square the pawn square to inspect
     * @param white whether the pawn belongs to White
     * @param friendlyPawns the friendly pawn list
     * @param enemyPawns the enemy pawn list
     * @return {@code true} when the pawn is backward
     */
    private static boolean isBackwardPawn(byte square, boolean white, List<Byte> friendlyPawns, List<Byte> enemyPawns) {
        int file = Field.getX(square);
        int rank = Field.getY(square);
        int forwardRank = white ? rank + 1 : rank - 1;
        if (forwardRank < 0 || forwardRank > 7) {
            return false;
        }
        AdjacentSupport support = adjacentSupport(file, white, friendlyPawns);
        if (!support.hasAdjacent || !support.isAdvancedEnough(rank, white)) {
            return false;
        }
        if (isPassedPawn(square, white, enemyPawns)) {
            return false;
        }
        return blockedByEnemyPawn(file, forwardRank, white, enemyPawns);
    }

    /**
     * Computes whether a pawn has adjacent friendly pawn support.
     *
     * @param file the pawn's file
     * @param white whether the pawn belongs to White
     * @param friendlyPawns the friendly pawn list
     * @return the adjacent support state and best supporting rank
     */
    private static AdjacentSupport adjacentSupport(int file, boolean white, List<Byte> friendlyPawns) {
        int bestRank = white ? -1 : 8;
        boolean hasAdjacent = false;
        for (byte pawn : friendlyPawns) {
            int pawnFile = Field.getX(pawn);
            if (Math.abs(pawnFile - file) == 1) {
                hasAdjacent = true;
                int pawnRank = Field.getY(pawn);
                bestRank = white ? Math.max(bestRank, pawnRank) : Math.min(bestRank, pawnRank);
            }
        }
        return new AdjacentSupport(hasAdjacent, bestRank);
    }

    /**
     * Checks whether an enemy pawn blocks the pawn's advance.
     *
     * @param file the pawn's file
     * @param forwardRank the pawn's immediate forward rank
     * @param white whether the pawn belongs to White
     * @param enemyPawns the enemy pawn list
     * @return {@code true} when an enemy pawn blocks the advance
     */
    private static boolean blockedByEnemyPawn(int file, int forwardRank, boolean white, List<Byte> enemyPawns) {
        for (byte enemy : enemyPawns) {
            int enemyRank = Field.getY(enemy);
            int enemyFile = Field.getX(enemy);
            if (isBackwardBlocker(file, forwardRank, white, enemyFile, enemyRank)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether an enemy pawn is the specific backward blocker.
     *
     * @param file the pawn's file
     * @param forwardRank the pawn's immediate forward rank
     * @param white whether the pawn belongs to White
     * @param enemyFile the enemy pawn file
     * @param enemyRank the enemy pawn rank
     * @return {@code true} when the enemy pawn qualifies as a blocker
     */
    private static boolean isBackwardBlocker(int file, int forwardRank, boolean white, int enemyFile, int enemyRank) {
        if (Math.abs(enemyFile - file) != 1) {
            return false;
        }
        return white ? enemyRank == forwardRank + 1 : enemyRank == forwardRank - 1;
    }

    /**
     * Adds connected-passed-pawn tags for adjacent passed pawns.
     *
     * @param tags the mutable tag accumulator
     * @param white whether the side being processed is White
     * @param passed the list of passed pawn squares
     */
    private static void addConnectedPassers(List<String> tags, boolean white, List<Byte> passed) {
        passed.sort(Byte::compare);
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
                    tags.add(FACT_PAWN_STRUCTURE_PREFIX + CONNECTED_PASSED + SIDE_FIELD + (white ? WHITE : BLACK)
                            + SQUARES_FIELD + Text.squareNameLower(a) + COMMA + Text.squareNameLower(b));
                }
            }
        }
    }

    /**
     * Formats a pawn-structure tag with side and square information.
     *
     * @param label the pawn-structure label
     * @param white whether the tag belongs to White
     * @param square the pawn square being described
     * @return the serialized pawn-structure tag
     */
    private static String formatPawnTag(String label, boolean white, byte square) {
        return FACT_PAWN_STRUCTURE_PREFIX + label + SIDE_FIELD + (white ? WHITE : BLACK) + SQUARE_FIELD
                + Text.squareNameLower(square);
    }

    /**
     * Captures the pawn positions and file counts for both sides.
     * <p>
     * The lists preserve pawn-square order while the arrays provide quick file
     * totals for doubled-pawn detection.
     * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
    private record PawnCollection(    List<Byte> whitePawns,     List<Byte> blackPawns,     int[] whiteFileCounts,
                        int[] blackFileCounts) {

         /**
         * Handles equals.
         * @param obj obj
         * @return computed value
         */
         @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PawnCollection other)) {
                return false;
            }
            return whitePawns.equals(other.whitePawns) && blackPawns.equals(other.blackPawns)
                    && Arrays.equals(whiteFileCounts, other.whiteFileCounts)
                    && Arrays.equals(blackFileCounts, other.blackFileCounts);
        }

         /**
         * Handles hash code.
         * @return computed value
         */
         @Override
        public int hashCode() {
            int result = whitePawns.hashCode();
            result = 31 * result + blackPawns.hashCode();
            result = 31 * result + Arrays.hashCode(whiteFileCounts);
            result = 31 * result + Arrays.hashCode(blackFileCounts);
            return result;
        }

         /**
         * Converts this value to string.
         * @return computed value
         */
         @Override
        public String toString() {
            return "PawnCollection[whitePawns=" + whitePawns + ", blackPawns=" + blackPawns + ", whiteFileCounts="
                    + Arrays.toString(whiteFileCounts) + ", blackFileCounts=" + Arrays.toString(blackFileCounts)
                    + ']';
        }
    }

    /**
     * Represents whether a pawn has adjacent support and the best supporting rank.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private record AdjacentSupport(    boolean hasAdjacent,     int bestRank) {

        /**
         * Determines whether the supporting pawn is advanced enough to matter.
         *
         * @param rank the pawn's own rank
         * @param white whether the pawn belongs to White
         * @return {@code true} when adjacent support is sufficiently advanced
         */
        private boolean isAdvancedEnough(int rank, boolean white) {
            return white ? bestRank > rank : bestRank < rank;
        }
    }
}
