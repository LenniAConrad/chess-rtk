package chess.tag.game;

import static chess.tag.core.Literals.LINE;
import static chess.tag.core.Literals.VARIATION;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import chess.core.Move;
import chess.core.Position;
import chess.struct.Game;
import utility.Json;

/**
 * Layer 3: variation analysis. Walks a {@link Game}'s move tree and runs the
 * multi-move {@link LineAnalyzer} over every <em>sideline</em> (PGN RAV), not
 * just the mainline, so a sacrifice or combination that only appears in an
 * annotator's variation is tagged with the same grounded {@code LINE:} motifs
 * the mainline gets.
 *
 * <p>{@link Game.Node}s carry only SAN, so each branch is resolved back to a
 * concrete move by generating the legal moves at the branch position and
 * matching their SAN ({@link SanResolver}). The resolved move sequence — the
 * mainline prefix up to the branch point followed by the variation's own moves —
 * is exactly the {@code (Position start, short[] moves)} pair
 * {@link LineAnalyzer} consumes. Branches that cannot be legally replayed
 * (malformed SAN, illegal move) are skipped rather than guessed at.</p>
 *
 * <p>Engine-free and fully grounded: every reported line is one that legally
 * replays from the game's start position. Nested variations (variations within
 * variations) are walked recursively, each emitted as its own line.</p>
 *
 * <p>On top of the per-sideline tags, a grounded <em>multi-variation tactic</em>
 * detector reports when the same tactical motif recurs across two or more
 * <em>sibling</em> sidelines — alternatives diverging from the same position
 * ("the same tactic refutes several tries"). It is sound by construction: it
 * groups siblings by the exact branch position (not the ply index), subtracts
 * any motif already present in the shared mainline prefix (so a prefix tactic
 * cannot masquerade as a shared one), and folds the benefiting side into the
 * compared descriptor so opposite-polarity motifs never merge.</p>
 *
 * <p>Emitted family {@code VARIATION:}:</p>
 * <ul>
 *   <li>{@code VARIATION: branch_ply=<n> length=<l> line="<san> <san> ..."} —
 *       one header per sideline, followed by that sideline's {@code LINE:} tags.</li>
 *   <li>{@code VARIATION: tactic_shared=<motif> branch_ply=<n> count=<k>
 *       detail="<payload>"} — a motif present in the divergent portion of {@code k}
 *       (≥ 2) sibling sidelines that share a branch position.</li>
 * </ul>
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class VariationAnalyzer {

    private VariationAnalyzer() {
        // utility
    }

    /**
     * Analyzes every sideline of a game.
     *
     * @param game the game whose variations to analyze
     * @return the structured result (never {@code null}; empty when {@code game}
     *     or its start position is {@code null})
     */
    public static Result analyze(Game game) {
        Result result = new Result();
        if (game == null || game.getStartPosition() == null) {
            return result;
        }
        Position start = game.getStartPosition();

        // Root variations branch at ply 0 — alternatives to the mainline's first move.
        for (Game.Node variation : game.getRootVariations()) {
            addLine(start, new short[0], "", variation, 0, start, result.variations);
        }

        // Walk the mainline. Per standard PGN RAV semantics a variation attached
        // to a node is an ALTERNATIVE to that node's move, so it branches from the
        // position BEFORE the move is played, with the prefix that reaches here.
        Position cur = start.copy();
        List<Short> prefix = new ArrayList<>();
        List<String> prefixSans = new ArrayList<>();
        Game.Node node = game.getMainline();
        while (node != null) {
            String parentPath = String.join(" ", prefixSans);
            for (Game.Node variation : node.getVariations()) {
                addLine(cur, toArray(prefix), parentPath, variation, prefix.size(), start,
                        result.variations);
            }
            short mv = SanResolver.resolve(cur, node.getSan());
            if (mv == Move.NO_MOVE) {
                break;
            }
            cur = cur.copy().play(mv);
            prefix.add(mv);
            prefixSans.add(node.getSan());
            node = node.getNext();
        }

        result.sharedTactics.addAll(sharedTactics(start, result.variations));
        return result;
    }

    /**
     * Replays one variation sub-line from a branch position, recurses into its
     * own nested variations, and records it (with its {@link LineAnalyzer} tags)
     * when at least one move resolved.
     *
     * @param branchPos the position the variation branches from
     * @param prefix the resolved moves leading to {@code branchPos}
     * @param parentPath the space-joined SAN of {@code prefix} (the path reaching
     *     the branch); distinguishes branches that reach the same position by
     *     different move orders
     * @param node the first node of the variation
     * @param branchPly the absolute ply index the variation diverges at
     * @param start the game's start position (for {@link LineAnalyzer})
     * @param out the accumulator to add the resulting {@link Variation} to
     */
    private static void addLine(Position branchPos, short[] prefix, String parentPath,
            Game.Node node, int branchPly, Position start, List<Variation> out) {
        List<Short> moves = new ArrayList<>();
        for (short m : prefix) {
            moves.add(m);
        }
        List<String> sans = new ArrayList<>();
        Position cur = branchPos.copy();
        Game.Node cursor = node;
        boolean truncated = false;
        while (cursor != null) {
            // A nested variation on this node is an alternative to its move, so it
            // branches from the position BEFORE the move (same RAV semantics).
            String nestedPath = parentPath.isEmpty()
                    ? String.join(" ", sans)
                    : parentPath + " " + String.join(" ", sans);
            for (Game.Node nested : cursor.getVariations()) {
                addLine(cur, toArray(moves), nestedPath.trim(), nested,
                        prefix.length + sans.size(), start, out);
            }
            short mv = SanResolver.resolve(cur, cursor.getSan());
            if (mv == Move.NO_MOVE) {
                truncated = true;
                break;
            }
            cur = cur.copy().play(mv);
            moves.add(mv);
            sans.add(cursor.getSan());
            cursor = cursor.getNext();
        }
        if (sans.isEmpty()) {
            return;
        }
        Variation variation = new Variation();
        variation.branchPly = branchPly;
        variation.sans = sans;
        variation.lines = LineAnalyzer.tags(start, toArray(moves));
        variation.branchKey = repKey(branchPos);
        variation.parentKey = parentPath;
        variation.prefixMoves = prefix.clone();
        variation.firstDivergentSan = sans.get(0);
        variation.fullyReplayed = !truncated;
        out.add(variation);
    }

    /**
     * Detects multi-variation tactics: a motif present in the divergent portion
     * of two or more sibling sidelines that share a branch position.
     *
     * <p>Grounded by construction — every input is an already-replay-proven
     * {@code LINE:} string. Siblings are grouped by the exact branch position
     * <em>and</em> the path reaching it (never the ply index), motifs already in
     * the shared mainline prefix are subtracted, and the benefiting side is part
     * of the compared descriptor, so the tag can never claim a tactic that is not
     * genuinely shared across divergent siblings.</p>
     *
     * @param start the game's start position
     * @param vars the analyzed sidelines
     * @return the {@code VARIATION: tactic_shared=...} tags, in deterministic order
     */
    private static List<String> sharedTactics(Position start, List<Variation> vars) {
        List<String> out = new ArrayList<>();
        if (start == null || vars == null || vars.isEmpty()) {
            return out;
        }
        boolean protagonistWhite = start.toString().split(" ")[1].equals("w");

        Map<String, List<Variation>> groups = new LinkedHashMap<>();
        for (Variation v : vars) {
            String key = v.branchKey + ' ' + v.parentKey;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
        }

        for (List<Variation> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            int groupPly = group.get(0).branchPly;
            boolean plyAgrees = true;
            for (Variation v : group) {
                if (v.branchPly != groupPly) {
                    plyAgrees = false;
                    break;
                }
            }
            if (!plyAgrees) {
                continue; // ambiguous group; never guess
            }

            // Motifs already present in the shared mainline prefix — these would
            // appear in every sibling spuriously, so they must not count.
            Set<String> baseline = new TreeSet<>();
            short[] prefixMoves = group.get(0).prefixMoves;
            if (prefixMoves.length > 0) {
                for (String line : LineAnalyzer.tags(start, prefixMoves)) {
                    String descriptor = motifDescriptor(line, protagonistWhite);
                    if (descriptor != null) {
                        baseline.add(descriptor);
                    }
                }
            }

            // descriptor -> distinct first-divergent SANs carrying it post-branch.
            Map<String, Set<String>> bySibling = new LinkedHashMap<>();
            for (Variation v : group) {
                if (!v.fullyReplayed) {
                    continue;
                }
                Set<String> sig = new TreeSet<>();
                for (String line : v.lines) {
                    String descriptor = motifDescriptor(line, protagonistWhite);
                    if (descriptor == null || baseline.contains(descriptor)) {
                        continue;
                    }
                    // Suffix-touch: the motif's line must include a post-branch move.
                    if (!lineTouchesSuffix(line, v.sans)) {
                        continue;
                    }
                    sig.add(descriptor);
                }
                for (String descriptor : sig) {
                    bySibling.computeIfAbsent(descriptor, k -> new TreeSet<>())
                            .add(v.firstDivergentSan);
                }
            }

            Set<String> emitted = new TreeSet<>();
            for (Map.Entry<String, Set<String>> e : bySibling.entrySet()) {
                if (e.getValue().size() < 2) {
                    continue;
                }
                String descriptor = e.getKey();
                int bar = descriptor.indexOf('|');
                String motif = bar < 0 ? descriptor : descriptor.substring(0, bar);
                String detail = bar < 0 ? "" : descriptor.substring(bar + 1).replace('|', ' ');
                emitted.add(VARIATION + ": tactic_shared=" + motif + " branch_ply=" + groupPly
                        + " count=" + e.getValue().size() + " detail=\"" + detail + "\"");
            }
            out.addAll(emitted);
        }
        return out;
    }

    /**
     * Reduces a raw {@code LINE:} string to a canonical
     * {@code motif|field=val|...|beneficiary=<side>} descriptor for cross-sibling
     * comparison, or {@code null} when the string is not a recognized motif.
     *
     * <p>The descriptor folds in the benefiting side so opposite-polarity motifs
     * never merge: material-bearing motifs (combination/sacrifice) read the sign
     * of their net field; the protagonist-delivered motifs (forcing /
     * perpetual_check / deflection) always benefit the protagonist (the side to
     * move at game start, constant across the game).</p>
     *
     * @param line a raw {@code LINE:} tag
     * @param protagonistWhite whether the protagonist (start side to move) is white
     * @return the descriptor, or {@code null}
     */
    private static String motifDescriptor(String line, boolean protagonistWhite) {
        if (line == null || !line.startsWith(LINE + ": motif=")) {
            return null;
        }
        String body = line.substring((LINE + ": ").length());
        String[] fields = body.split("\\s+");
        String motif = value(fields[0], "motif=");
        if (motif == null) {
            return null;
        }
        String protagonist = protagonistWhite ? "white" : "black";
        String opponent = protagonistWhite ? "black" : "white";
        switch (motif) {
            case "combination": {
                String outcome = field(fields, "outcome=");
                String beneficiary = signBeneficiary(field(fields, "nets="), protagonist, opponent);
                return "combination|outcome=" + outcome + "|beneficiary=" + beneficiary;
            }
            case "sacrifice": {
                String piece = field(fields, "piece=");
                String square = field(fields, "square=");
                String beneficiary = signBeneficiary(field(fields, "net="), protagonist, opponent);
                return "sacrifice|piece=" + piece + "|square=" + square
                        + "|beneficiary=" + beneficiary;
            }
            case "perpetual_check":
                return "perpetual_check|beneficiary=" + protagonist;
            case "forcing":
                return "forcing|beneficiary=" + protagonist;
            case "deflection":
                return "deflection|square=" + field(fields, "square=")
                        + "|beneficiary=" + protagonist;
            default:
                return null;
        }
    }

    /**
     * Returns whether a {@code LINE:} string's {@code line="..."} payload contains
     * at least one of the sideline's own (post-branch) SAN moves. A defense-in-depth
     * filter that can only suppress, never fabricate.
     *
     * @param line a raw {@code LINE:} tag
     * @param sans the sideline's post-branch SAN moves
     * @return {@code true} when the line cites a post-branch move
     */
    private static boolean lineTouchesSuffix(String line, List<String> sans) {
        int q = line.indexOf("line=\"");
        if (q < 0) {
            return false;
        }
        int end = line.indexOf('"', q + 6);
        String payload = end < 0 ? line.substring(q + 6) : line.substring(q + 6, end);
        for (String san : sans) {
            if (containsToken(payload, san)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether {@code token} appears as a whitespace-delimited token in
     * {@code text}.
     *
     * @param text the space-joined SAN payload
     * @param token the SAN move to find
     * @return {@code true} when present as a whole token
     */
    private static boolean containsToken(String text, String token) {
        for (String t : text.split("\\s+")) {
            if (t.equals(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Maps the sign of a net-material field value to the benefiting side.
     *
     * @param netValue the field value (a signed integer in text), or {@code null}
     * @param protagonist the protagonist's colour name
     * @param opponent the opponent's colour name
     * @return the benefiting side ({@code protagonist} when ≥ 0 or unparsable)
     */
    private static String signBeneficiary(String netValue, String protagonist, String opponent) {
        try {
            return Integer.parseInt(netValue) >= 0 ? protagonist : opponent;
        } catch (NumberFormatException ex) {
            return protagonist;
        }
    }

    /**
     * Returns the value of {@code key} among space-split fields, or {@code "?"}.
     *
     * @param fields the whitespace-split fields of a tag body
     * @param key the field key including its trailing {@code =}
     * @return the field value, or {@code "?"} when absent
     */
    private static String field(String[] fields, String key) {
        for (String f : fields) {
            String v = value(f, key);
            if (v != null) {
                return v;
            }
        }
        return "?";
    }

    /**
     * Returns the value of a single {@code key=value} token, or {@code null} when
     * it does not start with {@code key}.
     *
     * @param token a single field token
     * @param key the field key including its trailing {@code =}
     * @return the value, or {@code null}
     */
    private static String value(String token, String key) {
        return token.startsWith(key) ? token.substring(key.length()) : null;
    }

    /**
     * Position identity for sibling grouping: placement + side-to-move + castling
     * + en-passant (the first four FEN fields). Byte-identical to
     * {@code LineAnalyzer}'s repetition key.
     *
     * @param p the position
     * @return the four-field FEN key
     */
    private static String repKey(Position p) {
        String[] tok = p.toString().split(" ");
        int take = Math.min(4, tok.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < take; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(tok[i]);
        }
        return sb.toString();
    }

    /**
     * Copies a move list to a primitive array.
     *
     * @param moves the boxed moves
     * @return the moves as a {@code short[]}
     */
    private static short[] toArray(List<Short> moves) {
        short[] array = new short[moves.size()];
        for (int i = 0; i < moves.size(); i++) {
            array[i] = moves.get(i);
        }
        return array;
    }

    /**
     * One analyzed sideline: where it diverges, its own moves, and the grounded
     * {@code LINE:} tactics over the whole line from the game's start.
     */
    public static final class Variation {
        /**
         * Absolute ply index in the mainline at which this variation diverges.
         */
        public int branchPly;
        /**
         * The variation's own moves, in SAN, in order.
         */
        public List<String> sans = new ArrayList<>();
        /**
         * {@code LINE:} tags for the full line (mainline prefix + variation).
         */
        public List<String> lines = new ArrayList<>();
        /**
         * Four-field FEN identity of the position this sideline branches from;
         * the grouping key for multi-variation tactics.
         */
        public String branchKey = "";
        /**
         * Space-joined SAN path reaching the branch position; distinguishes
         * branches reaching the same position by different move orders.
         */
        public String parentKey = "";
        /**
         * The resolved mainline/sideline moves leading to the branch position;
         * used to subtract prefix motifs from the shared-tactic comparison.
         */
        public short[] prefixMoves = new short[0];
        /**
         * The first post-branch SAN — the move distinguishing this sibling.
         */
        public String firstDivergentSan = "";
        /**
         * Whether every annotated node of this sideline replayed legally.
         */
        public boolean fullyReplayed = false;

        /**
         * Renders this variation as canonical tags: a {@code VARIATION:} header
         * followed by its {@code LINE:} tags.
         *
         * @return the tag list for this variation
         */
        public List<String> tags() {
            List<String> tags = new ArrayList<>();
            tags.add(VARIATION + ": branch_ply=" + branchPly + " length=" + sans.size()
                    + " line=\"" + String.join(" ", sans) + "\"");
            tags.addAll(lines);
            return tags;
        }
    }

    /**
     * Structured variation analysis for a whole game.
     */
    public static final class Result {
        /**
         * The analyzed sidelines, in tree order.
         */
        public final List<Variation> variations = new ArrayList<>();
        /**
         * Multi-variation tactic tags ({@code VARIATION: tactic_shared=...}).
         */
        public final List<String> sharedTactics = new ArrayList<>();

        /**
         * Returns all variation tags flattened: each sideline's header and
         * {@code LINE:} tags, followed by the shared-tactic tags.
         *
         * @return the flattened tag list
         */
        public List<String> tags() {
            List<String> tags = new ArrayList<>();
            for (Variation variation : variations) {
                tags.addAll(variation.tags());
            }
            tags.addAll(sharedTactics);
            return tags;
        }

        /**
         * Serializes to nested JSON:
         * {@code {variations:[{branchPly, sans, lines}], sharedTactics:[...]}}.
         *
         * @return the JSON representation
         */
        public String toJson() {
            return "{\"variations\":" + arrayJson()
                    + ",\"sharedTactics\":"
                    + Json.stringArray(sharedTactics.toArray(new String[0])) + "}";
        }

        /**
         * Serializes just the variations as a JSON array
         * {@code [{branchPly, sans, lines}]}, for embedding in a larger document.
         *
         * @return the JSON array of variations
         */
        public String arrayJson() {
            StringBuilder sb = new StringBuilder(256);
            sb.append('[');
            for (int i = 0; i < variations.size(); i++) {
                Variation variation = variations.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"branchPly\":").append(variation.branchPly)
                        .append(",\"sans\":")
                        .append(Json.stringArray(variation.sans.toArray(new String[0])))
                        .append(",\"lines\":")
                        .append(Json.stringArray(variation.lines.toArray(new String[0])))
                        .append('}');
            }
            sb.append(']');
            return sb.toString();
        }
    }
}
