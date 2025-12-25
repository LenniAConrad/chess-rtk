package chess.struct;

import java.util.Objects;

import chess.core.Move;
import chess.core.Position;
import chess.uci.Analysis;
import chess.uci.Evaluation;
import chess.uci.Output;

/**
 * Utilities for serializing positions and engine outputs into the
 * <em>Leela-style plain</em> format used by your dataset.
 *
 * <p>
 * <strong>Record shape</strong> (one position per block, terminated by
 * {@code e}):
 * </p>
 *
 * <pre>
 * fen &lt;six-field FEN&gt;
 * move &lt;uci&gt;
 * score &lt;int&gt;
 * ply &lt;int&gt;
 * result &lt;-1|0|1&gt;
 * e
 * </pre>
 *
 * <ul>
 * <li><b>score</b>: centipawns if not mate; if mate, encode as
 * {@code sign * (VALUE_MATE - pliesToMate)} where sign is {@code +1}
 * if the side-to-move gives mate and {@code -1} if it is getting mated.</li>
 * <li><b>ply</b>: computed from FEN fullmove number and side-to-move as
 * {@code 2*(fullmove-1) + (stm==black ? 1 : 0)}.</li>
 * <li><b>result</b>: {-1,0,1} from the side-to-move perspective.
 * Mates are decisive by sign; otherwise we map centipawns to
 * win/draw/loss tendencies with a symmetric logistic and choose argmax.</li>
 * </ul>
 *
 * <p>
 * <strong>Thread-safety:</strong> stateless and thread-safe.
 * </p>
 */
public final class Plain {

    /**
     * Not instantiable.
     */
    private Plain() {
        // Intentionally empty: prevents instantiation.
    }

    /**
     * Symmetric centipawn band around zero that is treated as a draw.
     * Any evaluation with {@code |cp| <= DRAW_DEADZONE_CP} maps to {@code 0}
     * (side-to-move draw) in {@link #toResult(Evaluation)}; outside the band,
     * the sign determines win/loss.
     *
     * <p>
     * Units: centipawns. Typical tuning range: 20–60; {@code 40} is a
     * conservative default to reduce label flapping around equality.
     * </p>
     *
     * @since 2025
     * @see #toResult(Evaluation)
     */
    private static final int DRAW_DEADZONE_CP = 40;

    /**
     * Stockfish-style mate sentinel (safe, conventional choice).
     */
    public static final int VALUE_MATE = 32000;

    /**
     * Line separator used when building the plain text block.
     */
    private static final String NEW_LINE = System.lineSeparator();

    /**
     * Prefix for the FEN line: "fen <six-field-fen>".
     */
    private static final String KEY_FEN = "fen ";

    /**
     * Prefix for the move line in UCI: "move <uci>".
     */
    private static final String KEY_MOVE = "move ";

    /**
     * Prefix for the evaluation score line (centipawns or mate encoding): "score
     * <int>".
     */
    private static final String KEY_SCORE = "score ";

    /**
     * Prefix for the ply (half-move) index line: "ply <int>".
     */
    private static final String KEY_PLY = "ply ";

    /**
     * Prefix for the result from side to move: -1 (loss), 0 (draw), 1 (win).
     */
    private static final String KEY_RESULT = "result ";

    /**
     * End marker for a plain-formatted block: "e".
     */
    private static final String KEY_END = "e";

    /**
     * Convert an {@link Evaluation} to the plain integer {@code score}.
     *
     * <ul>
     * <li>Centipawns: returned unchanged.</li>
     * <li>Mate: encode as {@code sign * (VALUE_MATE - |pliesToMate|)},
     * where sign is {@code +1} if STM mates, {@code -1} otherwise.</li>
     * </ul>
     *
     * <p>
     * Examples: {@code "#3" -> 31997}, {@code "#-2" -> -31998},
     * {@code "123" -> 123}.
     * </p>
     *
     * @param eval non-null, valid evaluation
     * @return encoded score integer for the {@code score} line
     * @throws IllegalArgumentException if {@code eval} is null or invalid
     */
    private static int toScore(Evaluation eval) {
        if (eval == null || !eval.isValid()) {
            throw new IllegalArgumentException("Invalid evaluation");
        }
        if (!eval.isMate()) {
            return eval.getValue(); // centipawns
        }

        final int moves = Math.abs(eval.getValue()); // Stockfish UCI "mate N" = moves
        final int plies = moves * 2 - 1; // convert moves -> plies

        final int sign = Integer.signum(eval.getValue()); // +1 we mate, -1 we're mated
        return sign * (VALUE_MATE - plies);
    }

    /**
     * Compute ply-from-start from a {@link Position} that has a trustworthy
     * fullmove number and standard initial origin.
     *
     * <p>
     * Formula:
     * {@code ply = 2 * (fullmove - 1) + (stm == black ? 1 : 0)}.
     * </p>
     *
     * @param pos non-null position
     * @return half-move index (0 for the initial position)
     * @throws NullPointerException if {@code pos} is null
     */
    private static int toPly(Position pos) {
        Objects.requireNonNull(pos, "pos");
        return 2 * (pos.getFullMove() - 1) + (pos.isBlackTurn() ? 1 : 0);
    }

    /**
     * Maps an engine {@link Evaluation} to {-1,0,1} for the side-to-move.
     * Mates are decisive (use sign). Centipawns in
     * [-DRAW_DEADZONE_CP, +DRAW_DEADZONE_CP] → 0; otherwise use sign.
     * Guarantees {@code cp == 0 ⇒ 0}.
     *
     * @param eval non-null evaluation (centipawns unless
     *             {@link Evaluation#isMate()}).
     * @return {@code 1} win, {@code 0} draw, {@code -1} loss (STM perspective).
     * @throws IllegalArgumentException if {@code eval == null}.
     */
    private static int toResult(Evaluation eval) {
        if (eval == null) {
            throw new IllegalArgumentException("eval == null");
        }

        int v = eval.getValue();

        // Mates are decisive
        if (eval.isMate()) {
            return v > 0 ? 1 : -1;
        }

        // Hard draw band around 0 centipawns
        if (v == 0 || Math.abs(v) <= DRAW_DEADZONE_CP) {
            return 0;
        }

        // Outside the band: pick side by sign (simple & robust)
        return v > 0 ? 1 : -1;
    }

    /**
     * Render the given {@link Record} into Leela-style plain text.
     *
     * <p>
     * <b>Dispatcher:</b> This is a convenience wrapper that selects between
     * {@link #export(Record)} and {@link #exportAll(Record)}:
     * </p>
     * <ul>
     * <li>If {@code exportall == false}, it emits exactly one plain-formatted block
     * for the root position (equivalent to {@link #export(Record)}).</li>
     * <li>If {@code exportall == true}, it emits the root block <em>plus</em> one
     * block for each eligible alternative PV (PV ≥ 2) as described in
     * {@link #exportAll(Record)}. The result is a concatenation of one or more
     * blocks.</li>
     * </ul>
     *
     * <p>
     * <b>Output shape:</b> Each block has the canonical key order
     * </p>
     * 
     * <pre>
     * fen &lt;six-field FEN&gt;
     * move &lt;uci&gt;
     * score &lt;int&gt;
     * ply &lt;int&gt;
     * result &lt;-1|0|1&gt;
     * e
     * </pre>
     * <p>
     * and ends with a trailing newline. For {@code exportall == true}, the returned
     * string is the concatenation of such blocks, each terminated with {@code e}
     * and a newline.
     * </p>
     *
     * <p>
     * <b>When to use which:</b>
     * </p>
     * <ul>
     * <li>Use {@code exportall == false} when you want a single supervised target
     * for the root position (no multi-PV supervision).</li>
     * <li>Use {@code exportall == true} to additionally supervise alternative PVs
     * without assigning conflicting labels to the same root input; see
     * {@link #exportAll(Record)} for how child positions are constructed.</li>
     * </ul>
     *
     * <p>
     * <b>Thread-safety:</b> Stateless and thread-safe. This method does not
     * mutate {@code sample}; any move applications are done on position copies.
     * </p>
     *
     * @param sample    non-null record containing the root {@link Position} and
     *                  (for
     *                  multi-PV exports) an {@link Analysis}
     * @param exportall if {@code true}, include PV≥2 child blocks (multi-PV
     *                  export);
     *                  if {@code false}, export only the root block
     * @return a plain-formatted string: one block if {@code exportall == false},
     *         or one or more concatenated blocks if {@code exportall == true}
     * @throws NullPointerException     if {@code sample}, its position, or its
     *                                  analysis
     *                                  (when {@code exportall == true}) is
     *                                  {@code null}
     * @throws IllegalArgumentException if an {@link Evaluation} required for
     *                                  scoring is invalid (propagated from
     *                                  {@link #toScore(Evaluation)})
     * @see #export(Record)
     * @see #exportAll(Record)
     */
    public static String toString(Record sample, boolean exportall) {
        if (exportall) {
            return exportAll(sample);
        }
        return export(sample);
    }

    /**
     * Render a {@link Record} into a single, plain-formatted block with a trailing
     * newline.
     *
     * <p>
     * The method validates the presence of the required sub-objects
     * (position, best output, best move, evaluation) and throws informative
     * exceptions if anything is missing.
     * </p>
     *
     * @param sample non-null {@link Record} holding the position and analysis
     * @return plain-encoded text block for this record
     * @throws NullPointerException if {@code record} or any required component is
     *                              null
     */
    public static String export(Record sample) {
        Objects.requireNonNull(sample, "record");

        final Position position = Objects.requireNonNull(sample.getPosition(), "record.position");
        final Analysis analysis = Objects.requireNonNull(sample.getAnalysis(), "record.analysis");
        final Output best = Objects.requireNonNull(analysis.getBestOutput(), "analysis.bestOutput");
        final Evaluation eval = Objects.requireNonNull(best.getEvaluation(), "best.evaluation");

        // best move is a short (16-bit) in your setup
        final short bestMoveShort = analysis.getBestMove();
        final String uci = Move.toString(bestMoveShort);

        // Build in the canonical key order: fen, move, score, ply, result, e
        // Also make the string builder capacity generous to avoid re-allocs.

        return new StringBuilder(160)
                .append(KEY_FEN).append(position).append(NEW_LINE)
                .append(KEY_MOVE).append(uci).append(NEW_LINE)
                .append(KEY_SCORE).append(toScore(eval)).append(NEW_LINE)
                .append(KEY_PLY).append(toPly(position)).append(NEW_LINE)
                .append(KEY_RESULT).append(toResult(eval)).append(NEW_LINE)
                .append(KEY_END).append(NEW_LINE).toString();
    }

    /**
     * Exports the root PV and each available alternative PV (≥2) as a sequence of
     * plain-formatted blocks, concatenated in a single string.
     *
     * <p>
     * <strong>Why this exists:</strong> A plain block uses only the
     * <em>position</em> as input and a single target (move/score/result). If we
     * were to emit both the best and second-best decisions for the <em>same</em>
     * root position, we would create conflicting labels for an identical input.
     * To avoid this collision while still supervising alternative PVs, we advance
     * along each non-root PV so that its target is attached to a <em>different
     * input position</em> that reflects the consequences of following that PV.
     * </p>
     *
     * <p>
     * <strong>What it does:</strong>
     * </p>
     * <ol>
     * <li><b>PV1 (best line at the root):</b> Serialize the record with
     * {@link #export(Record)} (root position, best root move, root eval).</li>
     * <li><b>PV2..PV<em>k</em> (alternatives):</b> For each PV with at least three
     * moves <code>m0, m1, m2, ...</code>:
     * <ol type="a">
     * <li>Build a <em>child</em> position by applying the first two PV moves to
     * the root: <code>child = root.play(m0).play(m1)</code>. This yields the
     * natural decision point where the side-to-move must continue the
     * alternative line.</li>
     * <li>Emit a plain block for that child, choosing <code>m2</code> as the
     * <code>move</code> line, and using that PV's own {@link Evaluation}
     * for the <code>score</code> and derived <code>result</code>. The
     * <code>ply</code> is recomputed from the child FEN.</li>
     * </ol>
     * </li>
     * </ol>
     *
     * <p>
     * <strong>Intuition:</strong> This attaches each alternative PV to
     * the position that <em>embodies</em> its consequence, rather than forcing
     * multiple, incompatible labels on the same root input.
     * </p>
     *
     * <p>
     * <strong>Example (toy endgame):</strong>
     * </p>
     * <p>
     * Root position where only pushing the pawn wins; king moves draw:
     * </p>
     * 
     * <pre>
     * # Root PV1 (winning)
     * fen 8/8/7P/4k3/8/8/2K5/8 w - - 0 1
     * move h6h7
     * score 31983
     * ply 100
     * result 1
     * e
     * </pre>
     * <p>
     * If PV2 begins with a drawing try like <code>c2c3</code> and the best reply,
     * we advance two moves to the child and supervise the continuation from there:
     * </p>
     * 
     * <pre>
     * # PV2 child (drawn evaluation carried over from PV2)
     * fen 8/8/5k1P/8/8/2K5/8/8 w - - 2 2
     * move c3d4
     * score 0
     * ply 102
     * result 0
     * e
     * </pre>
     *
     * <p>
     * <strong>Preconditions &amp; skips:</strong> The method requires a non-null
     * <code>Record</code> with <code>position</code> and <code>analysis</code>.
     * For PV ≥ 2, entries lacking an {@link Evaluation} or fewer than three moves
     * are skipped (insufficient to form a decision point after two plies).
     * </p>
     *
     * <p>
     * <strong>Thread-safety:</strong> Does not mutate the input record; local
     * <code>Position</code> copies are used for move application.
     * </p>
     *
     * @param sample record containing the root position and multi-PV analysis
     * @return a concatenation of one or more plain-formatted blocks:
     *         first the root PV1 block, then zero or more PV≥2 child blocks
     *         derived as described above
     * @throws NullPointerException if {@code sample}, its position, or analysis is
     *                              null
     */
    public static String exportAll(Record sample) {
        Objects.requireNonNull(sample, "record");

        final StringBuilder sb = new StringBuilder();
        sb.append(export(sample)); // best move at root

        final Analysis analysis = Objects.requireNonNull(sample.getAnalysis(), "record.analysis");
        final int pivots = analysis.getPivots();

        // nothing else to do?
        if (pivots <= 1) {
            return sb.toString();
        }

        final Position root = Objects.requireNonNull(sample.getPosition(), "record.position");

        // For PV = 2..pivots: take its top move, play it to build the child position,
        // and serialize the child block with that PV's evaluation.

        for (int pv = 2; pv <= pivots; pv++) {
            Output out = analysis.getBestOutput(pv);
            Evaluation ev = (out != null) ? out.getEvaluation() : null;
            short[] moves = (out != null) ? out.getMoves() : null;

            // Single early-exit for all skip conditions
            if (ev == null || moves == null || moves.length < 3) {
                continue;
            }

            if (ev.isMate()) {
                ev = new Evaluation(true, ev.getValue() - 1);
            }

            Position child = root.copyOf().play(moves[0]).play(moves[1]);
            String uci = Move.toString(moves[2]);

            sb.append(KEY_FEN).append(child).append(NEW_LINE)
                    .append(KEY_MOVE).append(uci).append(NEW_LINE)
                    .append(KEY_SCORE).append(toScore(ev)).append(NEW_LINE)
                    .append(KEY_PLY).append(toPly(child)).append(NEW_LINE)
                    .append(KEY_RESULT).append(toResult(ev)).append(NEW_LINE)
                    .append(KEY_END).append(NEW_LINE);
        }
        return sb.toString();
    }

}
