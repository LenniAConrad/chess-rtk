package chess.tag.game;

import static chess.tag.core.Literals.LINE;

import chess.core.Position;
import chess.core.Move;
import chess.core.SAN;
import chess.eval.See;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * LAYER 2: tags a SEQUENCE of moves (a PV / variation / game segment).
 *
 * <p>Multi-move tactics that cannot be proven from a single static position
 * become GROUNDED by replaying the supplied moves and observing the result.
 * The line IS the proof; no search is performed. Every emitted tag is backed by
 * an invariant that held during the deterministic replay. If any supplied move
 * is illegal at its ply, replay stops there and the tags grounded by the legal
 * prefix are returned.</p>
 *
 * <p>PROTAGONIST = the side to move at {@code start}. Their plies are the even
 * indices 0,2,4,... The opponent's plies are the odd indices.</p>
 *
 * <p>Emitted lines are raw canonical strings of the form
 * {@code "LINE: motif=<id> ... line=\"<SAN ...>\""}.</p>
 *
 * <p>API notes (runtime-confirmed against this build):
 * Position.play(short) MUTATES the receiver AND returns it, so the
 * side-effect-free transition is {@code cur.copy().play(mv)} (the idiom the
 * engine itself uses in Position.generateSubPositions). Position exposes
 * inCheck() (NOT isCheck) and isCheckmate(); toString() yields a six-field FEN
 * (placement stm castling ep half full) usable as a repetition key. Move squares
 * come from Move.getFromIndex/getToIndex; MoveList exposes size() and get(int).
 * getBoard() returns SIGNED Piece codes, so material and piece identity are read
 * here from the FEN placement field (P/N/B/R/Q/K) to stay code-scheme-agnostic.</p>
 */
public final class LineAnalyzer {

    private LineAnalyzer() {}

    /**
     * Centipawn values keyed by upper-case FEN piece letter. King = 0.
     */
    private static int valueOf(char upperPiece) {
        switch (upperPiece) {
            case 'P': return 100;
            case 'N': return 320;
            case 'B': return 330;
            case 'R': return 500;
            case 'Q': return 900;
            default:  return 0; // K or anything else
        }
    }

    /**
     * Material threshold above which a combination's gain counts as decisive.
     */
    private static final int DECISIVE_CP = 300; // emit only when strictly greater

    /**
     * Derives deterministic line-level tags from a starting position and a legal
     * move sequence.
     *
     * @param start starting position
     * @param moves move sequence to replay
     * @return derived line tags
     */
    public static List<String> tags(Position start, short[] moves) {
        List<String> out = new ArrayList<>();
        if (start == null || moves == null || moves.length == 0) return out;

        // ---- Replay, validating legality at each ply --------------------------
        Position cur = start.copy();
        boolean protagonistWhite = cur.isWhiteToMove();

        List<Position> before = new ArrayList<>(); // before.get(i) = position before ply i
        List<Short> playable = new ArrayList<>();
        List<String> sanList = new ArrayList<>();

        // material balance from the protagonist's POV: balance.get(k) = value
        // after k plies. balance.get(0) is the pre-line baseline.
        List<Integer> balance = new ArrayList<>();
        balance.add(materialFor(cur, protagonistWhite));

        List<Boolean> wasCheck = new ArrayList<>();      // ply i delivered check
        List<Boolean> wasCapture = new ArrayList<>();    // ply i was a capture
        List<Boolean> wasMateThreat = new ArrayList<>(); // ply i created a mate-in-1 threat

        for (int i = 0; i < moves.length; i++) {
            short mv = moves[i];
            if (!isLegal(cur, mv)) break; // illegal -> stop, keep what we have

            String san;
            try {
                san = SAN.toAlgebraic(cur, mv);
            } catch (RuntimeException ex) {
                san = Move.toString(mv);
            }

            boolean capture = cur.isCapture(mv);

            before.add(cur.copy());
            playable.add(mv);
            sanList.add(san);
            wasCapture.add(capture);

            Position next = cur.copy().play(mv); // play mutates the copy, returns new state
            boolean check = next.inCheck();
            wasCheck.add(check);
            wasMateThreat.add(!check && hasMateInOneThreat(next));

            balance.add(materialFor(next, protagonistWhite));
            cur = next;
        }

        int n = playable.size();
        if (n == 0) return out;

        String fullSan = String.join(" ", sanList);
        Position end = cur; // position after the last played move

        int forcingRun = leadingForcingRun(n, wasCheck, wasCapture, wasMateThreat);

        emitCombination(out, playable, end, balance, wasCheck, wasCapture, wasMateThreat, fullSan);
        emitSacrifice(out, before, playable, balance, wasCapture, end, fullSan);
        emitPerpetual(out, start, playable, wasCheck, fullSan);

        if (forcingRun >= 2) {
            out.add(LINE + ": motif=forcing count=" + forcingRun + " line=\"" + fullSan + "\"");
        }

        emitDeflection(out, before, playable, wasCheck, wasCapture, fullSan);
        return out;
    }

    // ---------------------------------------------------------------------------
    // combination : every protagonist ply forcing; ends in mate OR decisive gain
    // ---------------------------------------------------------------------------
    private static void emitCombination(
            List<String> out, List<Short> mv, Position end, List<Integer> balance,
            List<Boolean> check, List<Boolean> capture, List<Boolean> mateThreat,
            String fullSan) {

        int n = mv.size();
        for (int i = 0; i < n; i += 2) {
            if (!(check.get(i) || capture.get(i) || mateThreat.get(i))) return;
        }

        boolean mate = end.isCheckmate();
        int net = balance.get(n) - balance.get(0); // protagonist POV, whole line

        String outcome;
        if (mate) {
            outcome = "mate";
        } else if (net > DECISIVE_CP) {
            outcome = "material";
        } else {
            return;
        }
        out.add(LINE + ": motif=combination length=" + n
                + " nets=" + net + " outcome=" + outcome
                + " line=\"" + fullSan + "\"");
    }

    // ---------------------------------------------------------------------------
    // sacrifice : protagonist material dips >= a pawn below baseline then
    //             recovers to >= baseline, or the line ends in mate
    // ---------------------------------------------------------------------------
    private static void emitSacrifice(
            List<String> out, List<Position> before, List<Short> mv,
            List<Integer> balance, List<Boolean> capture, Position end, String fullSan) {

        int n = mv.size();
        int base = balance.get(0);

        int dipPly = -1;
        for (int k = 1; k <= n; k++) {
            if (dipPly < 0 && balance.get(k) <= base - 100) dipPly = k;
        }
        if (dipPly < 0) return; // never invested material

        int finalBal = balance.get(n);
        boolean mate = end.isCheckmate();
        boolean recouped = (finalBal - base) >= 0;
        if (!recouped && !mate) return; // a plain blunder, not a sacrifice

        // The given-up piece: the last OPPONENT capture before the dip that
        // removed protagonist material.
        int sacPly = -1;
        for (int i = 0; i < dipPly && i < n; i++) {
            if (i % 2 == 1 && capture.get(i)) sacPly = i;
        }

        String piece = "?";
        String sq = "?";
        if (sacPly >= 0) {
            short omv = mv.get(sacPly);
            int dst = Move.getToIndex(omv);
            sq = squareName(dst);
            char letter = pieceLetterAt(before.get(sacPly), dst);
            if (letter != 0) piece = String.valueOf(Character.toUpperCase(letter));
        } else {
            int idx = Math.min(dipPly - 1, n - 1);
            if (idx >= 0) sq = squareName(Move.getToIndex(mv.get(idx)));
        }

        int net = finalBal - base;
        out.add(LINE + ": motif=sacrifice piece=" + piece + " square=" + sq
                + " recouped=" + (recouped || mate)
                + " net=" + net
                + " line=\"" + fullSan + "\"");
    }

    // ---------------------------------------------------------------------------
    // perpetual_check : protagonist checks every (even) ply AND a position repeats
    // ---------------------------------------------------------------------------
    private static void emitPerpetual(
            List<String> out, Position start, List<Short> mv,
            List<Boolean> check, String fullSan) {

        int n = mv.size();
        for (int i = 0; i < n; i += 2) {
            if (!check.get(i)) return;
        }
        if (n < 4) return; // need at least two protagonist checks to perpetuate

        Set<String> seen = new HashSet<>();
        seen.add(repKey(start));
        Position cur = start.copy();
        int checks = 0;
        boolean repeated = false;
        for (int i = 0; i < n; i++) {
            cur = cur.copy().play(mv.get(i));
            if (i % 2 == 0) checks++;
            if (!seen.add(repKey(cur))) repeated = true;
        }
        if (!repeated) return;

        out.add(LINE + ": motif=perpetual_check count=" + checks
                + " line=\"" + fullSan + "\"");
    }

    // ---------------------------------------------------------------------------
    // deflection (OPTIONAL) : ply i (check/capture) forces an enemy reply that
    //   vacates square X a defender was guarding; ply i+2 exploits X (SEE>0 or
    //   mate). Both halves proven by replay; emitted at most once, else skipped.
    // ---------------------------------------------------------------------------
    private static void emitDeflection(
            List<String> out, List<Position> before, List<Short> mv,
            List<Boolean> check, List<Boolean> capture, String fullSan) {

        int n = mv.size();
        for (int i = 0; i + 2 < n; i += 2) {
            if (!(check.get(i) || capture.get(i))) continue; // i must be forcing

            short reply = mv.get(i + 1);
            int defenderFrom = Move.getFromIndex(reply);
            int defenderTo = Move.getToIndex(reply);

            short exploit = mv.get(i + 2);
            int exploitTo = Move.getToIndex(exploit);

            if (defenderTo == exploitTo) continue; // defender re-guards, not deflected

            Position pExploit = before.get(i + 2);
            Position after = pExploit.copy().play(exploit);
            boolean mate = after.isCheckmate();
            int seeCp = pExploit.isCapture(exploit) ? See.see(pExploit, exploit) : Integer.MIN_VALUE;
            if (!(mate || seeCp > 0)) continue;

            Position pReply = before.get(i + 1);
            if (!guards(pReply, defenderFrom, exploitTo)) continue;

            out.add(LINE + ": motif=deflection square=" + squareName(exploitTo)
                    + " line=\"" + fullSan + "\"");
            return;
        }
    }

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

    private static boolean isLegal(Position p, short move) {
        var legal = p.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            if (legal.get(i) == move) return true;
        }
        return false;
    }

    /**
     * Material (centipawns) for {@code white}, read from the FEN placement field.
     * Encoding-agnostic: upper-case letters are White, lower-case Black.
     */
    private static int materialFor(Position p, boolean white) {
        String placement = p.toString().split(" ")[0];
        int mine = 0, theirs = 0;
        for (int i = 0; i < placement.length(); i++) {
            char c = placement.charAt(i);
            if (c == '/' || (c >= '1' && c <= '8')) continue;
            boolean isWhite = Character.isUpperCase(c);
            int v = valueOf(Character.toUpperCase(c));
            if (isWhite == white) mine += v; else theirs += v;
        }
        return mine - theirs;
    }

    /**
     * Repetition key: placement + side-to-move + castling + en-passant.
     */
    private static String repKey(Position p) {
        String[] tok = p.toString().split(" ");
        int take = Math.min(4, tok.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < take; i++) {
            if (i > 0) sb.append(' ');
            sb.append(tok[i]);
        }
        return sb.toString();
    }

    private static int leadingForcingRun(int n, List<Boolean> check,
            List<Boolean> capture, List<Boolean> mateThreat) {
        int run = 0;
        for (int i = 0; i < n; i += 2) {
            if (check.get(i) || capture.get(i) || mateThreat.get(i)) run++;
            else break;
        }
        return run;
    }

    /**
     * True if the side that just moved (NOT the side to move in {@code pos}) has
     * a mate-in-one available were it their turn. Grounded: flip the side to move
     * via a null move (FEN swap) and scan its legal moves for immediate mate.
     */
    private static boolean hasMateInOneThreat(Position pos) {
        Position threat = nullMove(pos);
        if (threat == null) return false;
        if (threat.inCheck()) return false;
        var legal = threat.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            if (threat.copy().play(legal.get(i)).isCheckmate()) return true;
        }
        return false;
    }

    /**
     * Returns a copy of pos with the side to move flipped (a null move).
     */
    private static Position nullMove(Position pos) {
        String[] tok = pos.toString().split(" ");
        if (tok.length < 2) return null;
        tok[1] = tok[1].equals("w") ? "b" : "w";
        if (tok.length >= 4) tok[3] = "-"; // clear en-passant for the null move
        try {
            return new Position(String.join(" ", tok));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * True if the piece on {@code fromSq} in {@code p} attacks {@code targetSq}.
     * Grounded by enumerating that piece's legal moves (from the perspective in
     * which it is its colour's turn) and checking for one landing on targetSq.
     */
    private static boolean guards(Position p, int fromSq, int targetSq) {
        char letter = pieceLetterAt(p, fromSq);
        if (letter == 0) return false;
        boolean pieceWhite = Character.isUpperCase(letter);
        Position probe = (p.isWhiteToMove() == pieceWhite) ? p : nullMove(p);
        if (probe == null) return false;
        var legal = probe.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            short m = legal.get(i);
            if (Move.getFromIndex(m) == fromSq && Move.getToIndex(m) == targetSq) return true;
        }
        return false;
    }

    /**
     * Returns the FEN piece letter standing on board index {@code sq} (0..63,
     * index = rank*8 + file with rank 0 = the 8th rank, matching this build's
     * getBoard()/Fen layout), or 0 if empty. Read from the FEN placement so it is
     * independent of the numeric board-code scheme.
     */
    private static char pieceLetterAt(Position p, int sq) {
        String placement = p.toString().split(" ")[0];
        int rank = sq >> 3;     // 0 = top of the FEN (8th rank)
        int file = sq & 7;
        String[] rows = placement.split("/");
        if (rank < 0 || rank >= rows.length) return 0;
        String row = rows[rank];
        int f = 0;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c >= '1' && c <= '8') {
                f += c - '0';
            } else {
                if (f == file) return c;
                f++;
            }
            if (f > file) break;
        }
        return 0;
    }

    /**
     * Square name for board index {@code sq}. This build's index layout has
     * rank 0 = the 8th rank (FEN top), so the human rank is 8 - (sq>>3).
     * Verified by the SAN traces in the self-test (Re8#, Ra8+, Bxd4, Qxf7+).
     */
    private static String squareName(int sq) {
        int file = sq & 7;
        int rankFromTop = sq >> 3;          // 0 = rank 8
        int humanRank = 8 - rankFromTop;    // 1..8
        return "" + (char) ('a' + file) + (char) ('0' + humanRank);
    }
}
