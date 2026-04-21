package chess.tag2;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;

/**
 * Heuristic-only position tags, rebuilt independently from {@code chess.tag}.
 *
 * <p>
 * This package deliberately avoids engine analysis, neural text, and the first
 * tagging pipeline. It starts with board facts, material, move counts, pawn
 * structure, basic tactics, and a few simple checkmate-pattern recognizers.
 * </p>
 */
public final class Tagging {

     /**
     * Shared start non king material constant.
     */
     private static final int START_NON_KING_MATERIAL = 7800;
     /**
     * Shared knight targets constant.
     */
     private static final byte[][] KNIGHT_TARGETS = Field.getJumps();
     /**
     * Shared king targets constant.
     */
     private static final byte[][] KING_TARGETS = Field.getNeighbors();
     /**
     * Shared white pawn attacks constant.
     */
     private static final byte[][] WHITE_PAWN_ATTACKS = Field.getPawnCaptureWhite();
     /**
     * Shared black pawn attacks constant.
     */
     private static final byte[][] BLACK_PAWN_ATTACKS = Field.getPawnCaptureBlack();
     /**
     * Shared diagonals constant.
     */
     private static final byte[][][] DIAGONALS = Field.getDiagonals();
     /**
     * Shared lines constant.
     */
     private static final byte[][][] LINES = Field.getLines();

     /**
     * Creates a new tagging instance.
     */
     private Tagging() {
        // utility
    }

    /**
     * Returns stable heuristic tags for a position.
     *
     * @param position the position to inspect
     * @return sorted and deduplicated tag strings
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");
        Context ctx = new Context(position);
        List<String> tags = new ArrayList<>(96);

        addMeta(tags, ctx);
        addFacts(tags, ctx);
        addMaterial(tags, ctx);
        addMoves(tags, ctx);
        addKingSafety(tags, ctx);
        addPawnStructure(tags, ctx);
        addPins(tags, ctx);
        addHangingPieces(tags, ctx);
        addForks(tags, ctx);
        addCheckmatePatterns(tags, ctx);

        return sort(tags);
    }

    /**
     * Returns tags as an array for callers that mirror the old tagging API.
     *
     * @param position the position to inspect
     * @return tag array
     */
    public static String[] positionalTags(Position position) {
        List<String> tags = tags(position);
        return tags.toArray(new String[0]);
    }

     /**
     * Handles add meta.
     * @param tags tags
     * @param ctx ctx
     */
     private static void addMeta(List<String> tags, Context ctx) {
        tags.add("META: to_move=" + side(ctx.whiteToMove));
        tags.add("META: phase=" + phase(ctx));
    }

     /**
     * Handles add facts.
     * @param tags tags
     * @param ctx ctx
     */
     private static void addFacts(List<String> tags, Context ctx) {
        tags.add("FACT: status=" + status(ctx));
        tags.add("FACT: in_check=" + (ctx.inCheck ? side(ctx.whiteToMove) : "none"));
        tags.add("FACT: castling=" + castlingRights(ctx.position));
        if (ctx.position.getEnPassant() != Field.NO_SQUARE) {
            tags.add("FACT: en_passant=" + square(ctx.position.getEnPassant()));
        }
        if (ctx.inCheck) {
            for (byte checker : ctx.position.getCheckers()) {
                byte piece = ctx.board[checker];
                tags.add("FACT: checker=" + side(Piece.isWhite(piece)) + "_" + pieceName(piece) + "@" + square(checker));
            }
        }
    }

     /**
     * Handles add material.
     * @param tags tags
     * @param ctx ctx
     */
     private static void addMaterial(List<String> tags, Context ctx) {
        tags.add("MATERIAL: cp_white=" + ctx.whiteMaterial);
        tags.add("MATERIAL: cp_black=" + ctx.blackMaterial);
        tags.add("MATERIAL: balance=" + materialBalance(ctx.whiteMaterial - ctx.blackMaterial));
        tags.add("MATERIAL: queens=" + presence(ctx.whiteQueens, ctx.blackQueens));
        tags.add("MATERIAL: rooks=" + presence(ctx.whiteRooks, ctx.blackRooks));

        String bishopPair = presence(ctx.whiteBishops >= 2 ? 1 : 0, ctx.blackBishops >= 2 ? 1 : 0);
        if (!"none".equals(bishopPair)) {
            tags.add("MATERIAL: bishop_pair=" + bishopPair);
        }
    }

     /**
     * Handles add moves.
     * @param tags tags
     * @param ctx ctx
     */
     private static void addMoves(List<String> tags, Context ctx) {
        int checks = 0;
        int captures = 0;
        int promotions = 0;
        int castles = 0;
        int mates = 0;

        for (int i = 0; i < ctx.moves.size(); i++) {
            short move = ctx.moves.get(i);
            if (isCapture(ctx, move)) {
                captures++;
            }
            if (Move.getPromotion(move) != 0) {
                promotions++;
            }
            if (isCastle(ctx, move)) {
                castles++;
            }
            Position next = ctx.position.copyOf().play(move);
            if (next.inCheck()) {
                checks++;
                if (next.getMoves().isEmpty()) {
                    mates++;
                }
            }
        }

        tags.add("MOVE: legal=" + ctx.moves.size());
        if (captures > 0) {
            tags.add("MOVE: captures=" + captures);
        }
        if (checks > 0) {
            tags.add("MOVE: checks=" + checks);
        }
        if (mates > 0) {
            tags.add("MOVE: mates=" + mates);
        }
        if (promotions > 0) {
            tags.add("MOVE: promotions=" + promotions);
        }
        if (castles > 0) {
            tags.add("MOVE: castles=" + castles);
        }
    }

     /**
     * Handles add king safety.
     * @param tags tags
     * @param ctx ctx
     */
     private static void addKingSafety(List<String> tags, Context ctx) {
        addKingSafety(tags, ctx, true, ctx.position.getWhiteKing());
        addKingSafety(tags, ctx, false, ctx.position.getBlackKing());
    }

     /**
     * Handles add king safety.
     * @param tags tags
     * @param ctx ctx
     * @param white white
     * @param king king
     */
     private static void addKingSafety(List<String> tags, Context ctx, boolean white, byte king) {
        if (king == Field.NO_SQUARE) {
            return;
        }
        int attackers = countAttackers(ctx.position, !white, king);
        int friendlyNeighbors = 0;
        int attackedNeighbors = 0;
        for (byte neighbor : KING_TARGETS[king]) {
            byte piece = ctx.board[neighbor];
            if (piece != Piece.EMPTY && Piece.isWhite(piece) == white) {
                friendlyNeighbors++;
            }
            if (countAttackers(ctx.position, !white, neighbor) > 0) {
                attackedNeighbors++;
            }
        }
        tags.add("KING: side=" + side(white) + " attackers=" + attackers);
        if (friendlyNeighbors <= 1 && attackedNeighbors >= 2) {
            tags.add("KING: exposed side=" + side(white));
        }
    }

     /**
     * Handles add pawn structure.
     * @param tags tags
     * @param ctx ctx
     */
     private static void addPawnStructure(List<String> tags, Context ctx) {
        addPawnStructure(tags, ctx, true);
        addPawnStructure(tags, ctx, false);
    }

     /**
     * Handles add pawn structure.
     * @param tags tags
     * @param ctx ctx
     * @param white white
     */
     private static void addPawnStructure(List<String> tags, Context ctx, boolean white) {
        int[] fileCounts = pawnFileCounts(ctx.board, white);
        for (int file = 0; file < 8; file++) {
            if (fileCounts[file] > 1) {
                tags.add("PAWN: doubled side=" + side(white) + " file=" + fileName(file) + " count=" + fileCounts[file]);
            }
            if (fileCounts[file] > 0 && noFriendlyPawnOnAdjacentFile(fileCounts, file)) {
                tags.add("PAWN: isolated side=" + side(white) + " file=" + fileName(file));
            }
        }

        byte pawn = white ? Piece.WHITE_PAWN : Piece.BLACK_PAWN;
        for (byte square = 0; square < ctx.board.length; square++) {
            if (ctx.board[square] != pawn) {
                continue;
            }
            int rank = Field.getY(square);
            if ((white && rank >= 5) || (!white && rank <= 2)) {
                tags.add("PAWN: advanced side=" + side(white) + " square=" + square(square));
            }
            if ((white && rank == 6) || (!white && rank == 1)) {
                tags.add("PAWN: promotion_ready side=" + side(white) + " square=" + square(square));
            }
            if (isPassedPawn(ctx.board, square, white)) {
                tags.add("PAWN: passed side=" + side(white) + " square=" + square(square));
            }
        }
    }

     /**
     * Handles add pins.
     * @param tags tags
     * @param ctx ctx
     */
     private static void addPins(List<String> tags, Context ctx) {
        for (byte square = 0; square < ctx.board.length; square++) {
            byte piece = ctx.board[square];
            if (piece == Piece.EMPTY || Piece.isKing(piece)) {
                continue;
            }
            Position.PinInfo pin = ctx.position.findPinToOwnKing(square);
            if (pin == null) {
                continue;
            }
            byte pinner = ctx.board[pin.pinnerSquare];
            tags.add("TACTIC: pin side=" + side(Piece.isWhite(piece))
                    + " piece=" + pieceName(piece)
                    + " square=" + square(square)
                    + " by=" + side(Piece.isWhite(pinner)) + "_" + pieceName(pinner) + "@" + square(pin.pinnerSquare));
        }
    }

     /**
     * Handles add hanging pieces.
     * @param tags tags
     * @param ctx ctx
     */
     private static void addHangingPieces(List<String> tags, Context ctx) {
        for (byte square = 0; square < ctx.board.length; square++) {
            byte piece = ctx.board[square];
            if (piece == Piece.EMPTY || Piece.isKing(piece)) {
                continue;
            }
            boolean white = Piece.isWhite(piece);
            boolean attacked = countAttackers(ctx.position, !white, square) > 0;
            boolean defended = countAttackers(ctx.position, white, square) > 0;
            if (attacked && !defended) {
                tags.add("TACTIC: hanging side=" + side(white)
                        + " piece=" + pieceName(piece)
                        + " square=" + square(square));
            }
        }
    }

     /**
     * Handles add forks.
     * @param tags tags
     * @param ctx ctx
     */
     private static void addForks(List<String> tags, Context ctx) {
        for (byte square = 0; square < ctx.board.length; square++) {
            byte piece = ctx.board[square];
            if (piece == Piece.EMPTY || Piece.isKing(piece)) {
                continue;
            }
            List<Target> targets = meaningfulTargets(ctx, square, piece);
            if (targets.size() >= 2) {
                tags.add("TACTIC: fork side=" + side(Piece.isWhite(piece))
                        + " piece=" + pieceName(piece)
                        + " square=" + square(square)
                        + " targets=" + targetList(targets));
            }
        }
    }

     /**
     * Handles add checkmate patterns.
     * @param tags tags
     * @param ctx ctx
     */
     private static void addCheckmatePatterns(List<String> tags, Context ctx) {
        if (!ctx.inCheck || !ctx.moves.isEmpty()) {
            return;
        }

        boolean matedWhite = ctx.whiteToMove;
        boolean winnerWhite = !matedWhite;
        byte king = matedWhite ? ctx.position.getWhiteKing() : ctx.position.getBlackKing();
        byte[] checkers = ctx.position.getCheckers();

        tags.add("CHECKMATE: winner=" + side(winnerWhite));
        tags.add("CHECKMATE: defender=" + side(matedWhite));
        for (byte checker : checkers) {
            byte piece = ctx.board[checker];
            tags.add("CHECKMATE: delivery=" + pieceName(piece));
        }
        if (checkers.length > 1) {
            tags.add("CHECKMATE: pattern=double_check");
        }
        if (isBackRankMate(ctx, matedWhite, king, checkers)) {
            tags.add("CHECKMATE: pattern=back_rank_mate");
        }
        if (isSmotheredMate(ctx, matedWhite, king, checkers)) {
            tags.add("CHECKMATE: pattern=smothered_mate");
        }
        if (isSupportMate(ctx, winnerWhite, king, checkers)) {
            tags.add("CHECKMATE: pattern=support_mate");
        }
        if (isCorner(king)) {
            tags.add("CHECKMATE: pattern=corner_mate");
        }
    }

     /**
     * Returns whether back rank mate.
     * @param ctx ctx
     * @param matedWhite mated white
     * @param king king
     * @param checkers checkers
     * @return true when back rank mate
     */
     private static boolean isBackRankMate(Context ctx, boolean matedWhite, byte king, byte[] checkers) {
        int rank = Field.getY(king);
        boolean homeRank = matedWhite ? rank == 0 : rank == 7;
        if (!homeRank) {
            return false;
        }
        for (byte checker : checkers) {
            byte piece = ctx.board[checker];
            if (Piece.isRook(piece) || Piece.isQueen(piece)) {
                return true;
            }
        }
        return false;
    }

     /**
     * Returns whether smothered mate.
     * @param ctx ctx
     * @param matedWhite mated white
     * @param king king
     * @param checkers checkers
     * @return true when smothered mate
     */
     private static boolean isSmotheredMate(Context ctx, boolean matedWhite, byte king, byte[] checkers) {
        if (checkers.length != 1 || !Piece.isKnight(ctx.board[checkers[0]])) {
            return false;
        }
        for (byte neighbor : KING_TARGETS[king]) {
            byte piece = ctx.board[neighbor];
            if (piece == Piece.EMPTY || Piece.isWhite(piece) != matedWhite) {
                return false;
            }
        }
        return true;
    }

     /**
     * Returns whether support mate.
     * @param ctx ctx
     * @param winnerWhite winner white
     * @param king king
     * @param checkers checkers
     * @return true when support mate
     */
     private static boolean isSupportMate(Context ctx, boolean winnerWhite, byte king, byte[] checkers) {
        if (checkers.length != 1) {
            return false;
        }
        byte checker = checkers[0];
        byte piece = ctx.board[checker];
        if (!Piece.isQueen(piece) || !isAdjacent(king, checker)) {
            return false;
        }
        return countAttackers(ctx.position, winnerWhite, checker) > 0;
    }

     /**
     * Returns whether capture.
     * @param ctx ctx
     * @param move move
     * @return true when capture
     */
     private static boolean isCapture(Context ctx, short move) {
        byte from = Move.getFromIndex(move);
        byte to = Move.getToIndex(move);
        byte moving = ctx.board[from];
        if (ctx.board[to] != Piece.EMPTY && Piece.isWhite(ctx.board[to]) != Piece.isWhite(moving)) {
            return true;
        }
        return Piece.isPawn(moving)
                && to == ctx.position.getEnPassant()
                && Field.getX(from) != Field.getX(to)
                && ctx.board[to] == Piece.EMPTY;
    }

     /**
     * Returns whether castle.
     * @param ctx ctx
     * @param move move
     * @return true when castle
     */
     private static boolean isCastle(Context ctx, short move) {
        byte from = Move.getFromIndex(move);
        byte to = Move.getToIndex(move);
        byte moving = ctx.board[from];
        byte target = ctx.board[to];
        return Piece.isKing(moving) && target != Piece.EMPTY && Piece.isWhite(target) == Piece.isWhite(moving)
                && Piece.isRook(target);
    }

     /**
     * Handles meaningful targets.
     * @param ctx ctx
     * @param square square
     * @param piece piece
     * @return computed value
     */
     private static List<Target> meaningfulTargets(Context ctx, byte square, byte piece) {
        List<Target> out = new ArrayList<>(4);
        for (byte targetSquare : attackedSquares(ctx.board, square, piece)) {
            byte targetPiece = ctx.board[targetSquare];
            if (targetPiece == Piece.EMPTY || Piece.isWhite(targetPiece) == Piece.isWhite(piece)) {
                continue;
            }
            boolean targetDefended = countAttackers(ctx.position, Piece.isWhite(targetPiece), targetSquare) > 0;
            if (Piece.isKing(targetPiece)
                    || Piece.getValue(targetPiece) >= Piece.getValue(piece)
                    || !targetDefended) {
                out.add(new Target(targetSquare, targetPiece));
            }
        }
        return out;
    }

     /**
     * Handles attacked squares.
     * @param board board
     * @param square square
     * @param piece piece
     * @return computed value
     */
     private static List<Byte> attackedSquares(byte[] board, byte square, byte piece) {
        List<Byte> out = new ArrayList<>(8);
        if (Piece.isPawn(piece)) {
            byte[][] table = Piece.isWhite(piece) ? WHITE_PAWN_ATTACKS : BLACK_PAWN_ATTACKS;
            addStaticTargets(out, table[square]);
        } else if (Piece.isKnight(piece)) {
            addStaticTargets(out, KNIGHT_TARGETS[square]);
        } else if (Piece.isKing(piece)) {
            addStaticTargets(out, KING_TARGETS[square]);
        } else {
            if (Piece.isBishop(piece) || Piece.isQueen(piece)) {
                addSlidingTargets(out, board, DIAGONALS[square]);
            }
            if (Piece.isRook(piece) || Piece.isQueen(piece)) {
                addSlidingTargets(out, board, LINES[square]);
            }
        }
        return out;
    }

     /**
     * Handles add static targets.
     * @param out out
     * @param targets targets
     */
     private static void addStaticTargets(List<Byte> out, byte[] targets) {
        for (byte target : targets) {
            out.add(target);
        }
    }

     /**
     * Handles add sliding targets.
     * @param out out
     * @param board board
     * @param rays rays
     */
     private static void addSlidingTargets(List<Byte> out, byte[] board, byte[][] rays) {
        for (byte[] ray : rays) {
            for (byte target : ray) {
                out.add(target);
                if (board[target] != Piece.EMPTY) {
                    break;
                }
            }
        }
    }

     /**
     * Handles pawn file counts.
     * @param board board
     * @param white white
     * @return computed value
     */
     private static int[] pawnFileCounts(byte[] board, boolean white) {
        int[] counts = new int[8];
        byte pawn = white ? Piece.WHITE_PAWN : Piece.BLACK_PAWN;
        for (byte square = 0; square < board.length; square++) {
            if (board[square] == pawn) {
                counts[Field.getX(square)]++;
            }
        }
        return counts;
    }

     /**
     * Handles no friendly pawn on adjacent file.
     * @param fileCounts file counts
     * @param file file
     * @return computed value
     */
     private static boolean noFriendlyPawnOnAdjacentFile(int[] fileCounts, int file) {
        return (file == 0 || fileCounts[file - 1] == 0) && (file == 7 || fileCounts[file + 1] == 0);
    }

     /**
     * Returns whether passed pawn.
     * @param board board
     * @param square square
     * @param white white
     * @return true when passed pawn
     */
     private static boolean isPassedPawn(byte[] board, byte square, boolean white) {
        int file = Field.getX(square);
        int rank = Field.getY(square);
        byte enemyPawn = white ? Piece.BLACK_PAWN : Piece.WHITE_PAWN;
        for (int df = -1; df <= 1; df++) {
            int f = file + df;
            if (f < 0 || f > 7) {
                continue;
            }
            for (int r = rank + (white ? 1 : -1); r >= 0 && r < 8; r += white ? 1 : -1) {
                if (board[Field.toIndex(f, r)] == enemyPawn) {
                    return false;
                }
            }
        }
        return true;
    }

     /**
     * Handles count attackers.
     * @param position position
     * @param byWhite by white
     * @param square square
     * @return computed value
     */
     private static int countAttackers(Position position, boolean byWhite, byte square) {
        return byWhite ? position.countAttackersByWhite(square) : position.countAttackersByBlack(square);
    }

     /**
     * Handles status.
     * @param ctx ctx
     * @return computed value
     */
     private static String status(Context ctx) {
        if (ctx.moves.isEmpty()) {
            return ctx.inCheck ? "checkmate" : "stalemate";
        }
        return "normal";
    }

     /**
     * Handles phase.
     * @param ctx ctx
     * @return computed value
     */
     private static String phase(Context ctx) {
        int total = ctx.whiteMaterial + ctx.blackMaterial;
        double ratio = total / (double) START_NON_KING_MATERIAL;
        if (ratio >= 0.75) {
            return "opening";
        }
        if (ratio >= 0.35) {
            return "middlegame";
        }
        return "endgame";
    }

     /**
     * Handles material balance.
     * @param diff diff
     * @return computed value
     */
     private static String materialBalance(int diff) {
        int abs = Math.abs(diff);
        if (abs < Piece.VALUE_PAWN) {
            return "equal";
        }
        String leader = diff > 0 ? "white" : "black";
        if (abs < Piece.VALUE_BISHOP) {
            return leader + "_up_pawn";
        }
        if (abs < Piece.VALUE_ROOK) {
            return leader + "_up_minor";
        }
        if (abs < Piece.VALUE_QUEEN) {
            return leader + "_up_rook";
        }
        return leader + "_up_large";
    }

     /**
     * Handles castling rights.
     * @param position position
     * @return computed value
     */
     private static String castlingRights(Position position) {
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
        return sb.isEmpty() ? "none" : sb.toString();
    }

     /**
     * Handles presence.
     * @param whiteCount white count
     * @param blackCount black count
     * @return computed value
     */
     private static String presence(int whiteCount, int blackCount) {
        boolean white = whiteCount > 0;
        boolean black = blackCount > 0;
        if (white && black) {
            return "both";
        }
        if (white) {
            return "white";
        }
        if (black) {
            return "black";
        }
        return "none";
    }

     /**
     * Handles sort.
     * @param tags tags
     * @return computed value
     */
     private static List<String> sort(List<String> tags) {
        List<String> cleaned = new ArrayList<>(tags.size());
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                cleaned.add(tag.trim());
            }
        }
        cleaned.sort((a, b) -> {
            int ra = familyRank(a);
            int rb = familyRank(b);
            if (ra != rb) {
                return Integer.compare(ra, rb);
            }
            return a.compareTo(b);
        });
        Set<String> unique = new LinkedHashSet<>(cleaned);
        return List.copyOf(unique);
    }

     /**
     * Handles family rank.
     * @param tag tag
     * @return computed value
     */
     private static int familyRank(String tag) {
        String family = tagFamily(tag);
        return switch (family) {
            case "META" -> 0;
            case "FACT" -> 1;
            case "MATERIAL" -> 2;
            case "MOVE" -> 3;
            case "KING" -> 4;
            case "PAWN" -> 5;
            case "TACTIC" -> 6;
            case "CHECKMATE" -> 7;
            default -> 99;
        };
    }

     /**
     * Handles tag family.
     * @param tag tag
     * @return computed value
     */
     private static String tagFamily(String tag) {
        int index = tag.indexOf(':');
        return index < 1 ? "" : tag.substring(0, index).trim();
    }

     /**
     * Handles target list.
     * @param targets targets
     * @return computed value
     */
     private static String targetList(List<Target> targets) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < targets.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Target target = targets.get(i);
            sb.append(pieceName(target.piece)).append('@').append(square(target.square));
        }
        return sb.toString();
    }

     /**
     * Returns whether adjacent.
     * @param a a
     * @param b b
     * @return true when adjacent
     */
     private static boolean isAdjacent(byte a, byte b) {
        return Math.abs(Field.getX(a) - Field.getX(b)) <= 1 && Math.abs(Field.getY(a) - Field.getY(b)) <= 1;
    }

     /**
     * Returns whether corner.
     * @param square square
     * @return true when corner
     */
     private static boolean isCorner(byte square) {
        int file = Field.getX(square);
        int rank = Field.getY(square);
        return (file == 0 || file == 7) && (rank == 0 || rank == 7);
    }

     /**
     * Handles side.
     * @param white white
     * @return computed value
     */
     private static String side(boolean white) {
        return white ? "white" : "black";
    }

     /**
     * Handles file name.
     * @param file file
     * @return computed value
     */
     private static String fileName(int file) {
        return Character.toString((char) ('a' + file));
    }

     /**
     * Handles square.
     * @param square square
     * @return computed value
     */
     private static String square(byte square) {
        return "" + (char) ('a' + Field.getX(square)) + (char) ('1' + Field.getY(square));
    }

     /**
     * Handles piece name.
     * @param piece piece
     * @return computed value
     */
     private static String pieceName(byte piece) {
        if (Piece.isPawn(piece)) {
            return "pawn";
        }
        if (Piece.isKnight(piece)) {
            return "knight";
        }
        if (Piece.isBishop(piece)) {
            return "bishop";
        }
        if (Piece.isRook(piece)) {
            return "rook";
        }
        if (Piece.isQueen(piece)) {
            return "queen";
        }
        if (Piece.isKing(piece)) {
            return "king";
        }
        return "empty";
    }

     /**
     * Provides context behavior.
     */
     private static final class Context {

         /**
         * Stores the position.
         */
         final Position position;
         /**
         * Stores the board.
         */
         final byte[] board;
         /**
         * Stores the moves.
         */
         final MoveList moves;
         /**
         * Stores the white to move.
         */
         final boolean whiteToMove;
         /**
         * Stores the in check.
         */
         final boolean inCheck;
         /**
         * Stores the white material.
         */
         final int whiteMaterial;
         /**
         * Stores the black material.
         */
         final int blackMaterial;
         /**
         * Stores the white queens.
         */
         final int whiteQueens;
         /**
         * Stores the black queens.
         */
         final int blackQueens;
         /**
         * Stores the white rooks.
         */
         final int whiteRooks;
         /**
         * Stores the black rooks.
         */
         final int blackRooks;
         /**
         * Stores the white bishops.
         */
         final int whiteBishops;
         /**
         * Stores the black bishops.
         */
         final int blackBishops;

         /**
         * Creates a new context instance.
         * @param position position
         */
         Context(Position position) {
            this.position = position;
            this.board = position.getBoard();
            this.moves = position.getMoves();
            this.whiteToMove = position.isWhiteTurn();
            this.inCheck = position.inCheck();

            int wm = 0;
            int bm = 0;
            int wq = 0;
            int bq = 0;
            int wr = 0;
            int br = 0;
            int wb = 0;
            int bb = 0;
            for (byte piece : board) {
                if (piece == Piece.EMPTY) {
                    continue;
                }
                if (Piece.isWhite(piece)) {
                    wm += Piece.getValue(piece);
                    wq += Piece.isQueen(piece) ? 1 : 0;
                    wr += Piece.isRook(piece) ? 1 : 0;
                    wb += Piece.isBishop(piece) ? 1 : 0;
                } else {
                    bm += Piece.getValue(piece);
                    bq += Piece.isQueen(piece) ? 1 : 0;
                    br += Piece.isRook(piece) ? 1 : 0;
                    bb += Piece.isBishop(piece) ? 1 : 0;
                }
            }
            this.whiteMaterial = wm;
            this.blackMaterial = bm;
            this.whiteQueens = wq;
            this.blackQueens = bq;
            this.whiteRooks = wr;
            this.blackRooks = br;
            this.whiteBishops = wb;
            this.blackBishops = bb;
        }
    }

     /**
     * Provides target behavior.
     */
     private static final class Target {

         /**
         * Stores the square.
         */
         final byte square;
         /**
         * Stores the piece.
         */
         final byte piece;

         /**
         * Creates a new target instance.
         * @param square square
         * @param piece piece
         */
         Target(byte square, byte piece) {
            this.square = square;
            this.piece = piece;
        }
    }
}
