package chess.struct;

import java.util.Locale;
import java.util.Map;

import chess.core.Fen;
import chess.core.Position;

/**
 * Resolves a PGN game's starting position from its setup tags.
 *
 * <p>Strict PGN requires {@code SetUp "1"} before a {@code FEN} tag is
 * authoritative. Real-world exports often omit the SetUp tag, so this helper
 * deliberately accepts FEN-without-SetUp as an import compatibility extension
 * and lets {@link Pgn#toPgn(Game)} normalize the game back to explicit
 * {@code SetUp "1"} output.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class PgnStartPosition {

    /**
     * PGN tag key for custom starting positions.
     */
    static final String FEN_TAG = "FEN";

    /**
     * PGN tag key that declares whether {@link #FEN_TAG} should be used.
     */
    static final String SETUP_TAG = "SetUp";

    /**
     * PGN tag key naming non-standard chess variants.
     */
    private static final String VARIANT_TAG = "Variant";

    /**
     * Utility class; prevents instantiation.
     */
    private PgnStartPosition() {
        // utility
    }

    /**
     * Resolves a start position from parsed PGN tags.
     *
     * @param tags parsed PGN tag map
     * @return resolved start position, falling back to standard chess on invalid input
     */
    static Position fromTags(Map<String, String> tags) {
        String fen = tags.get(FEN_TAG);
        String setup = tags.get(SETUP_TAG);
        if (fen == null || fen.isBlank() || setupDisablesFen(setup)) {
            return standardStart();
        }
        try {
            return new Position(startFenForTags(fen, tags.get(VARIANT_TAG)));
        } catch (IllegalArgumentException ex) {
            return standardStart();
        }
    }

    /**
     * Returns a fresh standard start position.
     *
     * @return standard chess start
     */
    private static Position standardStart() {
        return new Position(Game.STANDARD_START_FEN);
    }

    /**
     * Returns whether a SetUp tag explicitly disables the FEN tag.
     *
     * @param setup SetUp tag value, or null
     * @return true when FEN should be ignored
     */
    private static boolean setupDisablesFen(String setup) {
        return setup != null && "0".equals(setup.trim());
    }

    /**
     * Builds the FEN to parse from PGN tags.
     *
     * @param fen FEN tag value
     * @param variant Variant tag value, or null
     * @return FEN text ready for the strict core parser
     */
    private static String startFenForTags(String fen, String variant) {
        String normalized = Fen.normalize(fen);
        if (!isChess960Variant(variant)) {
            return normalized;
        }
        return normalizeChess960Castling(normalized);
    }

    /**
     * Returns whether a PGN Variant tag names Chess960/Fischer Random.
     *
     * @param variant Variant tag value
     * @return true for known Chess960 spellings
     */
    private static boolean isChess960Variant(String variant) {
        if (variant == null) {
            return false;
        }
        String value = variant.toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");
        return "chess960".equals(value)
                || "fischerandom".equals(value)
                || "fischerrandom".equals(value)
                || "fischerchess".equals(value);
    }

    /**
     * Converts Chess960 PGN X-FEN-style KQkq castling to Shredder-FEN file
     * letters before strict core parsing.
     *
     * @param fen normalized FEN
     * @return FEN with Chess960 castling letters when conversion applies
     */
    private static String normalizeChess960Castling(String fen) {
        String[] parts = fen.split(" ");
        if (parts.length != 4 && parts.length != 6) {
            return fen;
        }
        String castling = parts[2];
        if (castling.indexOf('-') >= 0 || !isStandardCastlingSubsequence(castling)) {
            return fen;
        }
        String converted = chess960CastlingLetters(parts[0], castling);
        if (converted == null) {
            return fen;
        }
        parts[2] = converted.isEmpty() ? "-" : converted;
        return String.join(" ", parts);
    }

    /**
     * Returns whether the castling field is a valid KQkq subsequence.
     *
     * @param castling FEN castling field
     * @return true when all symbols are ordered and unique standard castling symbols
     */
    private static boolean isStandardCastlingSubsequence(String castling) {
        if (castling == null || castling.isEmpty()) {
            return false;
        }
        int previous = -1;
        for (int i = 0; i < castling.length(); i++) {
            int order = switch (castling.charAt(i)) {
                case 'K' -> 0;
                case 'Q' -> 1;
                case 'k' -> 2;
                case 'q' -> 3;
                default -> -1;
            };
            if (order < 0 || order <= previous) {
                return false;
            }
            previous = order;
        }
        return true;
    }

    /**
     * Builds Shredder-FEN castling letters for a Chess960 board placement.
     *
     * @param placement FEN piece-placement field
     * @param castling KQkq-style castling availability
     * @return converted castling availability, or null when placement is malformed
     */
    private static String chess960CastlingLetters(String placement, String castling) {
        String[] ranks = placement.split("/");
        if (ranks.length != 8) {
            return null;
        }
        char[] blackBackRank = expandedFenRank(ranks[0]);
        char[] whiteBackRank = expandedFenRank(ranks[7]);
        if (blackBackRank == null || whiteBackRank == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(castling.length());
        if (castling.indexOf('K') >= 0
                && !appendChess960RookFile(out, whiteBackRank, 'K', 'R', true, true)) {
            return null;
        }
        if (castling.indexOf('Q') >= 0
                && !appendChess960RookFile(out, whiteBackRank, 'K', 'R', false, true)) {
            return null;
        }
        if (castling.indexOf('k') >= 0
                && !appendChess960RookFile(out, blackBackRank, 'k', 'r', true, false)) {
            return null;
        }
        if (castling.indexOf('q') >= 0
                && !appendChess960RookFile(out, blackBackRank, 'k', 'r', false, false)) {
            return null;
        }
        return out.toString();
    }

    /**
     * Expands one FEN rank to eight piece/empty characters.
     *
     * @param rank FEN rank segment
     * @return expanded rank, or null if malformed
     */
    private static char[] expandedFenRank(String rank) {
        char[] out = new char[8];
        int file = 0;
        for (int i = 0; i < rank.length(); i++) {
            char ch = rank.charAt(i);
            if (ch >= '1' && ch <= '8') {
                file += ch - '0';
            } else {
                if (file >= out.length) {
                    return null;
                }
                out[file++] = ch;
            }
        }
        return file == out.length ? out : null;
    }

    /**
     * Appends the rook file for one Chess960 castling side.
     *
     * @param out output builder
     * @param rank expanded back rank
     * @param king king piece character
     * @param rook rook piece character
     * @param kingside true for the h-side rook, false for the a-side rook
     * @param white true for uppercase file letters
     * @return true when a matching rook was found
     */
    private static boolean appendChess960RookFile(
            StringBuilder out,
            char[] rank,
            char king,
            char rook,
            boolean kingside,
            boolean white) {
        int kingFile = indexOf(rank, king);
        if (kingFile < 0) {
            return false;
        }
        int rookFile = kingside
                ? firstPieceFile(rank, rook, kingFile + 1, 1)
                : firstPieceFile(rank, rook, kingFile - 1, -1);
        if (rookFile < 0) {
            return false;
        }
        out.append((char) ((white ? 'A' : 'a') + rookFile));
        return true;
    }

    /**
     * Finds one piece on an expanded FEN rank.
     *
     * @param rank expanded rank
     * @param piece piece to find
     * @return zero-based file or -1
     */
    private static int indexOf(char[] rank, char piece) {
        for (int file = 0; file < rank.length; file++) {
            if (rank[file] == piece) {
                return file;
            }
        }
        return -1;
    }

    /**
     * Finds the first matching piece while walking one direction on a rank.
     *
     * @param rank expanded rank
     * @param piece piece to find
     * @param start first file to inspect
     * @param step +1 or -1
     * @return zero-based file or -1
     */
    private static int firstPieceFile(char[] rank, char piece, int start, int step) {
        for (int file = start; file >= 0 && file < rank.length; file += step) {
            if (rank[file] == piece) {
                return file;
            }
        }
        return -1;
    }
}
