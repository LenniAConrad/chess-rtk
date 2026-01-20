package chess.uci;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Predicate;

import utility.Booleans;

/**
 * Used for defining, composing, and evaluating rule trees over UCI engine
 * analysis output,
 * reducing multiple checks to a single boolean via a configurable logical gate.
 *
 * <p>
 * The rule evaluates one {@link Output} selected from the current principal
 * variation by a
 * break index, applies zero or more attribute predicates (e.g., depth, nodes,
 * nps, tbhits, time,
 * multipv, hashfull, {@link Evaluation}, {@link Chances}), optionally evaluates
 * nested child
 * rules (“leaves”), and then reduces all booleans with a {@link Gate} (AND, OR,
 * XOR, X_NOT_OR,
 * EQUAL, NOT_EQUAL, NOT_OR, NOT_AND).
 * </p>
 *
 * <p>
 * Create immutable rules with the fluent {@link Builder}. First set gate and
 * edge-case
 * behavior, then add predicates and child leaves. Rules are thread-safe and
 * reusable once
 * built; builders are not.
 * </p>
 *
 * <p>
 * Typical uses include:
 * </p>
 * <ul>
 * <li>Stopping a search when a forced mate or target centipawn advantage
 * appears at or beyond a
 * minimum depth.</li>
 * <li>Enforcing quality gates such as {@code depth>=X} and {@code nps>=Y}
 * before accepting a
 * line.</li>
 * <li>Capping search by {@code nodes} or {@code time} while still requiring
 * evaluation-based
 * conditions.</li>
 * <li>Working with MultiPV, endgame tablebase hits, and hash-table saturation
 * via
 * {@code multipv}, {@code tbhits}, and {@code hashfull} thresholds.</li>
 * <li>Comparing {@link Chances} (W/D/L) to probability targets.</li>
 * </ul>
 *
 * <p>
 * Example (use in a tactics/puzzle workflow; the engine stops as soon as the
 * rule turns
 * {@code true}):
 * </p>
 *
 * <pre>{@code
 * Engine engine = ...;
 * Position puzzle = ...;        // FEN of the puzzle start
 * Analysis analysis = new Analysis();
 *
 * Filter rule = Filter.builder()
 *     .gate(AND)
 *     .breakPrincipalVariation(1)
 *     .withDepth(Filter.ComparisonOperator.GREATER_EQUAL, 20) // require depth >= 20
 *     .withEvaluation(Filter.ComparisonOperator.GREATER_EQUAL, new Evaluation("#3"))
 *     .build();
 *
 * engine.analyse(puzzle, analysis, rule, 30_000_000L, 30_000L); // nodes, milliseconds
 * // analyse(...) will call rule.apply(...) repeatedly and stop once it returns true.
 * }</pre>
 *
 * <p>
 * Rules can be persisted to and restored from a compact DSL. Parse with
 * {@link FilterDSL#fromString(String)} and serialize with
 * {@link #toString()}.
 * The DSL supports comparison operators {@code >}, {@code >=}, {@code =},
 * {@code <=}, {@code <};
 * core keys {@code gate=}, {@code null=}, {@code empty=}, {@code break=};
 * predicate keys
 * {@code depth}, {@code seldepth}, {@code multipv}, {@code hashfull},
 * {@code nodes},
 * {@code nps}, {@code tbhits}, {@code time}, {@code eval}, {@code chances}; and
 * nested blocks
 * introduced by {@code leaf[ ... ]}.
 * </p>
 *
 * <p>
 * DSL example:
 * </p>
 *
 * <pre>{@code
 * String dsl = "gate=AND;null=false;empty=false;break=1;"
 *         + "depth>=20;eval>=#3;leaf[or;time>=25000,hashfull>=800]";
 *
 * Filter a = FilterDSL.fromString(dsl);
 * String persisted = a.toString(); // round-trippable representation
 * }</pre>
 *
 * <p>
 * Implementation notes: the evaluator selects the greatest {@link Output} at
 * the configured
 * break index and reduces predicate results via {@link utility.Booleans}
 * according to the chosen
 * {@link Gate}. String literals and symbols used by the DSL are centralized to
 * avoid
 * duplication.
 * </p>
 *
 * @see Engine#analyse(chess.core.Position, Analysis, Filter, long, long)
 * @see FilterDSL
 * @see Evaluation
 * @see Chances
 * @since 2025 (since 2023 but heavily modified in 2025)
 * @author Lennart A. Conrad
 */
public final class Filter {

    /**
     * Used for combining predicate results with a logical operation.
     */
    private final Gate gateOp;

    /**
     * Used for controlling return value when analysis is null or invalid.
     */
    private final boolean returnUponNull;

    /**
     * Used for controlling return value when no predicates or leaves are set.
     */
    private final boolean returnUponEmpty;

    /**
     * Used for defining how far into the principal variation to evaluate.
     */
    private final int breakPrincipalVariation;

    /**
     * Used for storing predicates to evaluate attributes of output.
     */
    private final List<Predicate<Output>> attributePredicates;

    /**
     * Used for holding nested argument trees to evaluate recursively.
     */
    private final List<Filter> leaves;

    /**
     * Immutable snapshot of all predicate specifications added via the builder.
     * This is used to round-trip an {@link Filter} tree to a compact DSL
     * (so the original operator/value per predicate can be serialized losslessly).
     *
     * <p>
     * Note: this is distinct from {@code attributePredicates}, which are the
     * executable {@link Predicate} instances. {@code predicateSpecs} captures the
     * declarative form for DSL I/O.
     * </p>
     */
    private final List<PredicateSpec> predicateSpecs;

    /**
     * Used for exposing an immutable snapshot of predicate specifications for DSL
     * round-trip.
     *
     * <p>
     * The returned list reflects builder-time declarations rather than executable
     * predicates.
     * </p>
     *
     * @return Used for returning an unmodifiable list of predicate specs.
     */
    List<PredicateSpec> getPredicateSpecs() {
        return predicateSpecs;
    }

    /**
     * Used for building a new Arguments.Builder instance.
     *
     * @return new builder object
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Constructs an immutable {@code Arguments} instance from a {@link Builder}.
     * Copies all builder state (gate, return policies, PV break point, predicates,
     * leaves, and predicate specs) into unmodifiable collections.
     *
     * @param b source builder (must be non-null and have a configured gate)
     */
    private Filter(Builder b) {
        this.gateOp = b.gateOp;
        this.returnUponNull = b.returnUponNull;
        this.returnUponEmpty = b.returnUponEmpty;
        this.breakPrincipalVariation = b.breakPrincipalVariation;
        this.attributePredicates = List.copyOf(b.attributePredicates);
        this.leaves = List.copyOf(b.leaves);
        this.predicateSpecs = List.copyOf(b.predicateSpecs);
    }

    /**
     * Used for evaluating this rule tree against an {@link Analysis} and reducing
     * all
     * checks with the configured {@link Gate}.
     *
     * <p>
     * If this node has no attribute predicates, PV access is skipped so internal
     * nodes can omit {@code break=}. If it has attribute predicates and
     * {@code break}
     * is {@code 0}, PV#1 is used by default.
     * </p>
     *
     * @param analysis Used for supplying engine outputs to inspect; may be
     *                 {@code null}.
     * @return Used for returning the gate-reduced boolean outcome.
     */
    public boolean apply(Analysis analysis) {
        final int attrCount = attributePredicates.size();
        final int leafCount = leaves.size();

        if (attrCount == 0 && leafCount == 0) {
            return returnUponEmpty;
        }

        Output greatest = null;
        if (attrCount > 0) {
            final int pvIndex = (breakPrincipalVariation > 0) ? breakPrincipalVariation : 1;
            greatest = (analysis == null) ? null : analysis.getBestOutput(pvIndex);
            if (greatest == null || !greatest.hasContent()) {
                return returnUponNull;
            }
        }

        final boolean[] checked = new boolean[attrCount + leafCount];
        int i = 0;

        if (attrCount > 0) {
            for (Predicate<Output> p : attributePredicates) {
                checked[i++] = p.test(greatest);
            }
        }
        for (Filter leaf : leaves) {
            checked[i++] = leaf.apply(analysis);
        }

        return reduceWithGate(checked);
    }

    /**
     * Reduces an array of booleans using the configured {@link Gate}.
     *
     * <p>
     * Semantics (delegated to {@code utility.Booleans}):
     * </p>
     * <ul>
     * <li>{@code AND}: true if all are true</li>
     * <li>{@code NOT_AND}: logical negation of AND</li>
     * <li>{@code OR}: true if any is true</li>
     * <li>{@code NOT_OR}: logical negation of OR</li>
     * <li>{@code XOR}: true if an odd number of values are true</li>
     * <li>{@code X_NOT_OR} (XNOR): true if an even number of values are true</li>
     * <li>{@code SAME}: true if all values are identical</li>
     * <li>{@code NOT_SAME}: true if not all values are identical</li>
     * </ul>
     *
     * @param checked evaluated predicate results (must not be {@code null})
     * @return the gate-reduced boolean
     */
    private boolean reduceWithGate(boolean[] checked) {
        return switch (gateOp) {
            case AND -> Booleans.and(checked);
            case NOT_AND -> Booleans.notAnd(checked);
            case OR -> Booleans.or(checked);
            case NOT_OR -> Booleans.notOr(checked);
            case XOR -> Booleans.xOr(checked);
            case X_NOT_OR -> Booleans.xNotOr(checked);
            case SAME -> Booleans.same(checked);
            case NOT_SAME -> Booleans.notSame(checked);
        };
    }

    /**
     * Comparison operators used by numeric and domain-specific predicates.
     * These map to the natural ordering of the compared type.
     */
    public enum ComparisonOperator {
        GREATER, GREATER_EQUAL, EQUAL, LESS_EQUAL, LESS
    }

    /**
     * Logical reduction operators used to combine multiple boolean checks into one.
     * See {@link #reduceWithGate(boolean[])} for the exact semantics.
     */
    public enum Gate {
        AND, NOT_AND, OR, NOT_OR, XOR, X_NOT_OR, SAME, NOT_SAME
    }

    /**
     * Serializes this {@code Arguments} tree to a compact, stable DSL string.
     * Uses {@code predicateSpecs} to preserve operator/value detail of predicates.
     *
     * @return DSL representation (e.g.
     *         {@code gate=AND;null=false;empty=false;break=1;depth>=20;...})
     */
    @Override
    public String toString() {
        return FilterDSL.toString(this);
    }

    /**
     * Used for producing an immutable deep copy of another {@code Arguments} tree.
     *
     * <p>
     * Collections and children are defensively copied; the result shares no mutable
     * state with the
     * source.
     * </p>
     *
     * @param other Used for providing the source rule tree to copy; must be
     *              non-null.
     * @return Used for returning a new, independent {@code Arguments} instance.
     */
    public Filter copyOf(Filter other) {
        Objects.requireNonNull(other, "other");

        Builder b = Filter.builder()
                .gate(other.gateOp)
                .returnUponNull(other.returnUponNull)
                .returnUponEmpty(other.returnUponEmpty)
                .breakPrincipalVariation(other.breakPrincipalVariation);

        addPredicatesFromSpecs(b, other.predicateSpecs);
        copyLeaves(b, other.leaves);

        return b.build();
    }

    /**
     * Used for adding predicates to the builder from declarative specs.
     *
     * @param b     Used for receiving the predicates.
     * @param specs Used for supplying the predicate specifications; may be
     *              {@code null} or empty.
     */
    private void addPredicatesFromSpecs(Builder b, List<PredicateSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return;
        }
        for (PredicateSpec s : specs) {
            applySpec(b, s);
        }
    }

    /**
     * Used for applying a single predicate specification to the builder.
     *
     * @param b Used for receiving the built predicate.
     * @param s Used for supplying the predicate spec to translate.
     */
    private void applySpec(Builder b, PredicateSpec s) {
        switch (s.kind) {
            // scalar longs
            case NODES -> b.withNodes(s.op, Long.parseLong(s.value));
            case NPS -> b.withNps(s.op, Long.parseLong(s.value));
            case TBHITS -> b.withTbhits(s.op, Long.parseLong(s.value));
            case TIME -> b.withTime(s.op, Long.parseLong(s.value));
            // scalar ints
            case DEPTH -> b.withDepth(s.op, Integer.parseInt(s.value));
            case SELDEPTH -> b.withSelDepth(s.op, Integer.parseInt(s.value));
            case MULTIPV -> b.withMultiPV(s.op, Integer.parseInt(s.value));
            case HASHFULL -> b.withHashfull(s.op, Integer.parseInt(s.value));
            // complex types
            case EVAL -> b.withEvaluation(s.op, parseEvaluation(s.value));
            case CHANCES -> b.withChances(s.op, parseChances(s.value));
            default -> {
                /* no-op */
            }
        }
    }

    /**
     * Used for parsing an {@link Evaluation} from its canonical string or
     * {@code null}.
     *
     * @param value Used for supplying the canonical value string.
     * @return Used for returning the parsed evaluation or {@code null}.
     */
    private static Evaluation parseEvaluation(String value) {
        return (value == null || DslLiterals.NULL_LITERAL.equals(value)) ? null : new Evaluation(value);
    }

    /**
     * Used for parsing {@link Chances} from its canonical string or {@code null}.
     *
     * @param value Used for supplying the canonical value string.
     * @return Used for returning the parsed chances or {@code null}.
     */
    private static Chances parseChances(String value) {
        return (value == null || DslLiterals.NULL_LITERAL.equals(value)) ? null : Chances.parse(value);
    }

    /**
     * Used for deep-copying and attaching child leaves from another rule tree.
     *
     * @param b      Used for receiving copied leaves.
     * @param leaves Used for supplying source leaves; may be {@code null} or empty.
     */
    private void copyLeaves(Builder b, List<Filter> leaves) {
        if (leaves == null || leaves.isEmpty()) {
            return;
        }
        for (Filter leaf : leaves) {
            b.addLeaf(copyOf(leaf));
        }
    }

    /**
     * Used for building immutable {@code Arguments} instances.
     */
    public static final class Builder {

        /**
         * Used for storing the gate operation for predicate combination.
         */
        private Gate gateOp = Gate.AND; // default

        /**
         * Used for determining behavior when analysis is null or invalid.
         */
        private boolean returnUponNull = false; // default

        /**
         * Used for determining behavior when no predicates or leaves are set.
         */
        private boolean returnUponEmpty = false; // default

        /**
         * Used for defining how far into the principal variation to evaluate.
         *
         * <p>
         * Semantics: a value &gt; 0 selects that PV index. A value of {@code 0} means
         * this node
         * does not bind a PV; attribute predicates (if any) default to PV#1, and
         * leaf-only nodes
         * skip PV access entirely. This lets internal DSL nodes omit {@code break=} for
         * readability.
         * </p>
         */
        private int breakPrincipalVariation = 0;

        /**
         * Used for storing output attribute predicates.
         */
        private final List<Predicate<Output>> attributePredicates = new ArrayList<>();

        /**
         * Used for storing nested Arguments rules to be evaluated recursively.
         */
        private final List<Filter> leaves = new ArrayList<>();

        /** Predicate specs for DSL round-trip. */
        private final List<PredicateSpec> predicateSpecs = new ArrayList<>();

        /**
         * Used for setting the gate operation for logical combination.
         *
         * @param gate the gate operator to use
         * @return this builder instance
         */
        public Builder gate(Gate gate) {
            this.gateOp = Objects.requireNonNull(gate, "gate");
            return this;
        }

        /**
         * Used for setting behavior when analysis is null or invalid.
         *
         * @param val true to return upon null analysis
         * @return this builder instance
         */
        public Builder returnUponNull(boolean val) {
            this.returnUponNull = val;
            return this;
        }

        /**
         * Used for setting behavior when no predicates or leaves exist.
         *
         * @param val true to return upon empty conditions
         * @return this builder instance
         */
        public Builder returnUponEmpty(boolean val) {
            this.returnUponEmpty = val;
            return this;
        }

        /**
         * Used for setting break point in principal variation.
         *
         * @param val number of moves into variation
         * @return this builder instance
         */
        public Builder breakPrincipalVariation(int val) {
            this.breakPrincipalVariation = val;
            return this;
        }

        /**
         * Adds a predicate on {@code nodes} (visited node count).
         *
         * @param op        comparison operator to apply
         * @param threshold right-hand value for the comparison
         * @return this builder
         */
        public Builder withNodes(ComparisonOperator op, long threshold) {
            Objects.requireNonNull(op, "nodes op");
            attributePredicates.add(o -> o != null && compareLong(o.getNodes(), op, threshold));
            predicateSpecs.add(new PredicateSpec(PredicateSpec.Kind.NODES, op, Long.toString(threshold)));
            return this;
        }

        /**
         * Adds a predicate on {@code nps} (nodes per second).
         *
         * @param op        comparison operator to apply
         * @param threshold right-hand value for the comparison
         * @return this builder
         */
        public Builder withNps(ComparisonOperator op, long threshold) {
            Objects.requireNonNull(op, "nps op");
            attributePredicates.add(o -> o != null && compareLong(o.getNodesPerSecond(), op, threshold));
            predicateSpecs.add(new PredicateSpec(PredicateSpec.Kind.NPS, op, Long.toString(threshold)));
            return this;
        }

        /**
         * Adds a predicate on {@code tbhits} (tablebase hits).
         *
         * @param op        comparison operator to apply
         * @param threshold right-hand value for the comparison
         * @return this builder
         */
        public Builder withTbhits(ComparisonOperator op, long threshold) {
            Objects.requireNonNull(op, "tbhits op");
            attributePredicates.add(o -> o != null && compareLong(o.getTableBaseHits(), op, threshold));
            predicateSpecs.add(new PredicateSpec(PredicateSpec.Kind.TBHITS, op, Long.toString(threshold)));
            return this;
        }

        /**
         * Adds a predicate on elapsed {@code time} in milliseconds.
         *
         * @param op        comparison operator to apply
         * @param threshold right-hand value for the comparison
         * @return this builder
         */
        public Builder withTime(ComparisonOperator op, long threshold) {
            Objects.requireNonNull(op, "time op");
            attributePredicates.add(o -> o != null && compareLong(o.getTime(), op, threshold));
            predicateSpecs.add(new PredicateSpec(PredicateSpec.Kind.TIME, op, Long.toString(threshold)));
            return this;
        }

        /**
         * Adds a predicate on search {@code depth}.
         *
         * @param op        comparison operator to apply
         * @param threshold right-hand value for the comparison
         * @return this builder
         */
        public Builder withDepth(ComparisonOperator op, int threshold) {
            Objects.requireNonNull(op, "depth op");
            attributePredicates.add(o -> o != null && compareInt(o.getDepth(), op, threshold));
            predicateSpecs.add(new PredicateSpec(PredicateSpec.Kind.DEPTH, op, Integer.toString(threshold)));
            return this;
        }

        /**
         * Adds a predicate on selective depth ({@code seldepth}).
         *
         * @param op        comparison operator to apply
         * @param threshold right-hand value for the comparison
         * @return this builder
         */
        public Builder withSelDepth(ComparisonOperator op, int threshold) {
            Objects.requireNonNull(op, "seldepth op");
            attributePredicates.add(o -> o != null && compareInt(o.getSelectiveDepth(), op, threshold));
            predicateSpecs.add(new PredicateSpec(PredicateSpec.Kind.SELDEPTH, op, Integer.toString(threshold)));
            return this;
        }

        /**
         * Adds a predicate on {@code multipv} (MultiPV index / count depending on
         * usage).
         *
         * @param op        comparison operator to apply
         * @param threshold right-hand value for the comparison
         * @return this builder
         */
        public Builder withMultiPV(ComparisonOperator op, int threshold) {
            Objects.requireNonNull(op, "multipv op");
            attributePredicates.add(o -> o != null && compareInt(o.getPrincipalVariation(), op, threshold));
            predicateSpecs.add(new PredicateSpec(PredicateSpec.Kind.MULTIPV, op, Integer.toString(threshold)));
            return this;
        }

        /**
         * Adds a predicate on {@code hashfull} (0..1000; 1000 ≈ 100%).
         *
         * @param op        comparison operator to apply
         * @param threshold right-hand value for the comparison
         * @return this builder
         */
        public Builder withHashfull(ComparisonOperator op, int threshold) {
            Objects.requireNonNull(op, "hashfull op");
            attributePredicates.add(o -> o != null && compareInt(o.getHashfull(), op, threshold));
            predicateSpecs.add(new PredicateSpec(PredicateSpec.Kind.HASHFULL, op, Integer.toString(threshold)));
            return this;
        }

        /**
         * Used for adding an evaluation predicate that compares score/mate values
         * according to the
         * {@link Evaluation} domain ordering (e.g., mates outrank centipawns as defined
         * by
         * {@link Evaluation}).
         *
         * @param op     Used for choosing the comparison operator.
         * @param target Used for providing the evaluation to compare against; may be
         *               {@code null}.
         * @return Used for enabling fluent builder chaining.
         */
        public Builder withEvaluation(ComparisonOperator op, Evaluation target) {
            Objects.requireNonNull(op, "eval op");
            attributePredicates
                    .add(o -> evalMatchesWithBound(o, op, target));
            // Choose a stable/compact string representation for Evaluation:
            String ev = (target == null) ? DslLiterals.NULL_LITERAL : target.toString();
            predicateSpecs.add(new PredicateSpec(PredicateSpec.Kind.EVAL, op, ev));
            return this;
        }

        /**
         * Used for adding a result-probabilities predicate on {@link Chances} (W/D/L).
         * Maps the binary
         * {@link ComparisonOperator} onto the tri-component comparison defined by
         * {@link Chances}.
         *
         * @param op     Used for choosing the comparison operator to map.
         * @param target Used for providing the target W/D/L triple; may be
         *               {@code null}.
         * @return Used for enabling fluent builder chaining.
         */
        public Builder withChances(ComparisonOperator op, Chances target) {
            Objects.requireNonNull(op, "chances op");
            attributePredicates
                    .add(o -> o != null && o.getChances() != null && compareChances(o.getChances(), op, target));
            // Choose a stable/compact string representation for Chances:
            String ch = (target == null) ? DslLiterals.NULL_LITERAL : target.toString();
            predicateSpecs.add(new PredicateSpec(PredicateSpec.Kind.CHANCES, op, ch));
            return this;
        }

        /**
         * Used for appending a nested Arguments rule to this rule.
         *
         * @param rule the child Arguments instance
         * @return this builder instance
         */
        public Builder addLeaf(Filter rule) {
            this.leaves.add(Objects.requireNonNull(rule));
            return this;
        }

        /**
         * Used for building the Arguments instance from current builder state.
         *
         * @return constructed Arguments object
         */
        public Filter build() {
            Objects.requireNonNull(gateOp, "gateOp not set");
            return new Filter(this);
        }

        /**
         * Compares two {@code long} values using the given operator.
         *
         * @param v  left-hand value
         * @param op comparison operator to apply
         * @param t  right-hand threshold
         * @return {@code true} if the comparison holds
         */
        private static boolean compareLong(long v, ComparisonOperator op, long t) {
            return switch (op) {
                case GREATER -> v > t;
                case GREATER_EQUAL -> v >= t;
                case EQUAL -> v == t;
                case LESS_EQUAL -> v <= t;
                case LESS -> v < t;
            };
        }

        /**
         * Compares two {@code int} values using the given operator.
         * Delegates to {@link #compareLong(long, ComparisonOperator, long)}.
         *
         * @param v  left-hand value
         * @param op comparison operator to apply
         * @param t  right-hand threshold
         * @return {@code true} if the comparison holds
         */
        private static boolean compareInt(int v, ComparisonOperator op, int t) {
            return compareLong(v, op, t);
        }

        /**
         * Compares two {@link Evaluation} instances using domain-specific ordering
         * (e.g., mate scores vs centipawn scores).
         *
         * @param e  left-hand evaluation
         * @param op comparison operator to apply
         * @param t  right-hand evaluation to compare against
         * @return {@code true} if the comparison holds
         */
        private static boolean compareEval(Evaluation e, ComparisonOperator op, Evaluation t) {
            return switch (op) {
                case GREATER -> e.isGreater(t);
                case GREATER_EQUAL -> e.isGreaterEqual(t);
                case EQUAL -> e.isEqual(t);
                case LESS_EQUAL -> e.isLessEqual(t);
                case LESS -> e.isLess(t);
            };
        }

        /**
         * Used for comparing an {@link Evaluation} while respecting bound hints from
         * the engine. Lower bounds only satisfy {@code >}/{@code >=}, upper bounds only
         * satisfy {@code <}/{@code <=}, and exact scores are required for equality.
         *
         * @param o      output holding evaluation/bound metadata
         * @param op     comparison operator to apply
         * @param target evaluation target to compare against
         * @return {@code true} when the comparison is allowed and passes
         */
        private static boolean evalMatchesWithBound(Output o, ComparisonOperator op, Evaluation target) {
            if (o == null) {
                return false;
            }
            Evaluation e = o.getEvaluation();
            if (e == null) {
                return false;
            }
            Output.Bound b = o.getBound();
            // Reject comparisons that would turn a one-sided bound into a misleading pass.
            if (b == Output.Bound.LOWER && (op == ComparisonOperator.LESS || op == ComparisonOperator.LESS_EQUAL || op == ComparisonOperator.EQUAL)) {
                return false;
            }
            if (b == Output.Bound.UPPER && (op == ComparisonOperator.GREATER || op == ComparisonOperator.GREATER_EQUAL || op == ComparisonOperator.EQUAL)) {
                return false;
            }
            if (b != Output.Bound.NONE && op == ComparisonOperator.EQUAL) {
                return false;
            }
            return compareEval(e, op, target);
        }

        /**
         * Compares two {@link Chances} (W/D/L) values by mapping the binary operator
         * to the tri-component comparison provided by {@link Chances}.
         *
         * @param c  left-hand chances
         * @param op comparison operator to apply
         * @param t  right-hand target chances
         * @return {@code true} if the comparison holds
         */
        private static boolean compareChances(Chances c, ComparisonOperator op, Chances t) {
            // Preserve original tri-operator semantics:
            return switch (op) {
                case GREATER -> c.compare(
                        Chances.ComparisonOperator.GREATER,
                        Chances.ComparisonOperator.GREATER_EQUAL,
                        Chances.ComparisonOperator.LESS,
                        t);
                case GREATER_EQUAL -> c.compare(
                        Chances.ComparisonOperator.GREATER_EQUAL,
                        Chances.ComparisonOperator.GREATER_EQUAL,
                        Chances.ComparisonOperator.LESS_EQUAL,
                        t);
                case EQUAL -> c.compare(
                        Chances.ComparisonOperator.EQUAL,
                        Chances.ComparisonOperator.EQUAL,
                        Chances.ComparisonOperator.EQUAL,
                        t);
                case LESS_EQUAL -> c.compare(
                        Chances.ComparisonOperator.LESS_EQUAL,
                        Chances.ComparisonOperator.LESS_EQUAL,
                        Chances.ComparisonOperator.GREATER_EQUAL,
                        t);
                case LESS -> c.compare(
                        Chances.ComparisonOperator.LESS,
                        Chances.ComparisonOperator.LESS_EQUAL,
                        Chances.ComparisonOperator.GREATER,
                        t);
            };
        }
    }

    /**
     * Used for centralizing DSL string literals to eliminate duplication in
     * parser/serializer.
     * Place inside {@code Arguments.ArgumentsDSL} (near the top) as a
     * {@code private static final}
     * nested class.
     */
    private static final class DslLiterals {

        /** Used for representing the canonical literal for a {@code null} value. */
        static final String NULL_LITERAL = "null";

        /** Used for the DSL key prefix for the gate operator. */
        static final String KEY_GATE = "gate=";

        /** Used for the DSL key prefix for null-return behavior. */
        static final String KEY_NULL = "null=";

        /** Used for the DSL key prefix for empty-return behavior. */
        static final String KEY_EMPTY = "empty=";

        /** Used for the DSL key prefix for principal variation break. */
        static final String KEY_BREAK = "break=";

        /** Used for the informational predicates count key. */
        static final String KEY_PREDICATES = "predicates=";

        /** Used for the token starting a nested leaf block. */
        static final String LEAF_OPEN = "leaf[";

        /** Used for the token ending a nested leaf block. */
        static final String LEAF_CLOSE_AND_SEMI = "];";

        /** Used for the predicate key "nodes". */
        static final String PRED_NODES = "nodes";

        /** Used for the predicate key "nps". */
        static final String PRED_NPS = "nps";

        /** Used for the predicate key "tbhits". */
        static final String PRED_TBHITS = "tbhits";

        /** Used for the predicate key "time". */
        static final String PRED_TIME = "time";

        /** Used for the predicate key "depth". */
        static final String PRED_DEPTH = "depth";

        /** Used for the predicate key "seldepth". */
        static final String PRED_SELDEPTH = "seldepth";

        /** Used for the predicate key "multipv". */
        static final String PRED_MULTIPV = "multipv";

        /** Used for the predicate key "hashfull". */
        static final String PRED_HASHFULL = "hashfull";

        /** Used for the predicate key "eval". */
        static final String PRED_EVAL = "eval";

        /** Used for the predicate key "chances". */
        static final String PRED_CHANCES = "chances";

        /** Used for the DSL delimiter semicolon. */
        static final char DELIM_SEMICOLON = ';';

        /** Used for the DSL delimiter comma. */
        static final char DELIM_COMMA = ',';

        /** Used for the DSL space delimiter during parsing. */
        static final char DELIM_SPACE = ' ';

        /** Used for the closing bracket encountered by the parser. */
        static final char BRACKET_CLOSE = ']';

        /** Used for the opening bracket encountered by the parser. */
        static final char BRACKET_OPEN = '[';

        /** Used for the DSL symbol for GREATER. */
        static final String OP_SYMBOL_GREATER = ">";

        /** Used for the DSL symbol for GREATER_EQUAL. */
        static final String OP_SYMBOL_GREATER_EQUAL = ">=";

        /** Used for the DSL symbol for EQUAL. */
        static final String OP_SYMBOL_EQUAL = "=";

        /** Used for the DSL symbol for LESS_EQUAL. */
        static final String OP_SYMBOL_LESS_EQUAL = "<=";

        /** Used for the DSL symbol for LESS. */
        static final String OP_SYMBOL_LESS = "<";

        /**
         * Prevents instantiation of this utility class.
         */
        private DslLiterals() {
            /* Used for preventing instantiation. */
        }
    }

    /**
     * Utilities for parsing/serializing {@link Filter} trees from/to
     * a compact, human-readable DSL.
     *
     * <p>
     * Example:
     * </p>
     * 
     * <pre>
     * gate=AND;null=false;empty=false;break=1;depth>=31;seldepth>=60;multipv=1;nodes>=116712829;nps>=3978756;hashfull>=628;tbhits=0;time>=29334;eval>=0;leaf[gate=AND;break=2;nodes>=1000000;eval<=0]
     * </pre>
     */
    public static final class FilterDSL {

        /** Hidden constructor for static-only utility class. */
        private FilterDSL() {

        }

        /**
         * Used for parsing a compact DSL string into an {@code Arguments} tree.
         *
         * <p>
         * Accepts key/value tokens, comparison expressions, and nested
         * {@code leaf[...]} blocks.
         * </p>
         *
         * @param dsl Used for supplying the DSL text to parse; trimmed but otherwise
         *            unmodified.
         * @return Used for returning the parsed {@code Arguments} instance.
         * @throws IllegalArgumentException Used for signaling malformed input.
         */
        public static Filter fromString(String dsl) {
            return new Parser(dsl).parse();
        }

        /**
         * Used for serializing an {@code Arguments} tree into its compact DSL form.
         * Preserves operator and value detail via predicate specs when available.
         *
         * <p>
         * Note: omits {@code break=} when it is {@code 0} and omits the informational
         * {@code predicates=} token when there are no attribute predicates; this keeps
         * internal nodes readable.
         * </p>
         *
         * @param arg Used for providing the rule tree to serialize.
         * @return Used for returning a DSL string that {@link #fromString(String)} can
         *         parse.
         */
        public static String toString(Filter arg) {
            StringBuilder sb = new StringBuilder(256);

            // Core properties
            sb.append(DslLiterals.KEY_GATE).append(arg.gateOp).append(';')
                    .append(DslLiterals.KEY_NULL).append(arg.returnUponNull).append(';')
                    .append(DslLiterals.KEY_EMPTY).append(arg.returnUponEmpty).append(';');

            // Only emit break when explicitly set (> 0). Default (0) means "unbound".
            if (arg.breakPrincipalVariation > 0) {
                sb.append(DslLiterals.KEY_BREAK).append(arg.breakPrincipalVariation).append(';');
            }

            // Predicates (lossless via predicateSpecs). If none, omit "predicates=0".
            if (arg.predicateSpecs != null && !arg.predicateSpecs.isEmpty()) {
                for (PredicateSpec s : arg.predicateSpecs) {
                    sb.append(s.toDsl()).append(';');
                }
            } else if (arg.attributePredicates != null && !arg.attributePredicates.isEmpty()) {
                sb.append(DslLiterals.KEY_PREDICATES)
                        .append(arg.attributePredicates.size())
                        .append(';');
            }

            // Recurse into leaves
            if (arg.leaves != null && !arg.leaves.isEmpty()) {
                for (Filter leaf : arg.leaves) {
                    sb.append(DslLiterals.LEAF_OPEN)
                            .append(FilterDSL.toString(leaf))
                            .append(DslLiterals.LEAF_CLOSE_AND_SEMI);
                }
            }
            return sb.toString();
        }

        /**
         * Hand-rolled, single-pass parser for the Arguments DSL.
         * Consumes key/value tokens, comparison expressions, and balanced leaf blocks.
         */
        private static final class Parser {

            /** Raw DSL input, trimmed once at construction. */
            private final String input;

            /** Current index into {@link #input}. */
            private int pos = 0;

            /**
             * Creates a parser for the given input.
             * 
             * @param input DSL text (non-null)
             */
            Parser(String input) {
                this.input = Objects.requireNonNull(input).trim();
            }

            /**
             * Used for parsing the entire input into an {@link Filter} instance while
             * keeping the method
             * small and flat. Advances until end of input and skips unknown tokens.
             *
             * @return Used for returning the parsed {@link Filter} instance.
             */
            Filter parse() {
                Filter.Builder b = Filter.builder();
                while (pos < input.length()) {
                    skipWs();
                    if (!dispatchToken(b)) {
                        pos++;
                    }
                    skipDelims();
                }
                return b.build();
            }

            /**
             * Used for dispatching the next recognized token into builder mutations.
             *
             * @param b Used for receiving parsed properties and predicates.
             * @return Used for indicating whether a token was consumed.
             */
            private boolean dispatchToken(Filter.Builder b) {
                if (peek(DslLiterals.KEY_GATE)) {
                    pos += 5;
                    b.gate(Filter.Gate.valueOf(readToken()));
                    return true;
                }
                if (peek(DslLiterals.KEY_NULL)) {
                    pos += 5;
                    b.returnUponNull(Boolean.parseBoolean(readToken()));
                    return true;
                }
                if (peek(DslLiterals.KEY_EMPTY)) {
                    pos += 6;
                    b.returnUponEmpty(Boolean.parseBoolean(readToken()));
                    return true;
                }
                if (peek(DslLiterals.KEY_BREAK)) {
                    pos += 6;
                    b.breakPrincipalVariation(parseInt(readToken()));
                    return true;
                }
                if (handleLongComparisons(b)) {
                    return true;
                }
                if (handleIntComparisons(b)) {
                    return true;
                }
                if (handleEval(b)) {
                    return true;
                }
                if (handleChances(b)) {
                    return true;
                }
                if (handlePredicatesInfo()) {
                    return true;
                }
                return handleLeaf(b);
            }

            /**
             * Used for handling long-valued comparison tokens: nodes, nps, tbhits, time.
             *
             * <|diff_marker|> ADD A2060
             * 
             * @param b Used for receiving the predicates.
             * @return Used for indicating whether a token was matched.
             */
            private boolean handleLongComparisons(Filter.Builder b) {
                if (peek(DslLiterals.PRED_NODES)) {
                    long v = parseLongCompAndValue();
                    b.withNodes(lastOp, v);
                    return true;
                }
                if (peek(DslLiterals.PRED_NPS)) {
                    long v = parseLongCompAndValue();
                    b.withNps(lastOp, v);
                    return true;
                }
                if (peek(DslLiterals.PRED_TBHITS)) {
                    long v = parseLongCompAndValue();
                    b.withTbhits(lastOp, v);
                    return true;
                }
                if (peek(DslLiterals.PRED_TIME)) {
                    long v = parseLongCompAndValue();
                    b.withTime(lastOp, v);
                    return true;
                }
                return false;
            }

            /**
             * Used for handling int-valued comparison tokens: depth, seldepth, multipv,
             * hashfull.
             *
             * @param b Used for receiving the predicates.
             * @return Used for indicating whether a token was matched.
             */
            private boolean handleIntComparisons(Filter.Builder b) {
                if (peek(DslLiterals.PRED_DEPTH)) {
                    int v = parseIntCompAndValue();
                    b.withDepth(lastOp, v);
                    return true;
                }
                if (peek(DslLiterals.PRED_SELDEPTH)) {
                    int v = parseIntCompAndValue();
                    b.withSelDepth(lastOp, v);
                    return true;
                }
                if (peek(DslLiterals.PRED_MULTIPV)) {
                    int v = parseIntCompAndValue();
                    b.withMultiPV(lastOp, v);
                    return true;
                }
                if (peek(DslLiterals.PRED_HASHFULL)) {
                    int v = parseIntCompAndValue();
                    b.withHashfull(lastOp, v);
                    return true;
                }
                return false;
            }

            /**
             * Used for parsing an {@code eval} comparison (centipawns or mate) allowing
             * decimals.
             *
             * <p>
             * Examples: {@code eval>=300}, {@code eval<-50}, {@code eval>=#3}.
             * </p>
             *
             * @param b Used for receiving the parsed comparison.
             * @return Used for indicating whether a token was matched.
             */
            private boolean handleEval(Filter.Builder b) {
                if (!peek(DslLiterals.PRED_EVAL)) {
                    return false;
                }
                String token = readComparison(); // e.g., "eval>=300" or "eval>=#3"
                Filter.ComparisonOperator op = parseOp(token);
                String raw = token.replaceAll("[^0-9#.+-]", "");
                Evaluation eval = (raw.isEmpty() || DslLiterals.NULL_LITERAL.equals(raw)) ? null : parseEval(raw);
                b.withEvaluation(op, eval);
                return true;
            }

            /**
             * Parses a raw eval token into an {@link Evaluation} instance.
             * Accepts mate markers and decimal pawn values.
             *
             * @param raw raw token value (e.g., "#3", "-0.5", "300")
             * @return parsed evaluation instance
             */
            private static Evaluation parseEval(String raw) {
                String normalized = raw;
                if (normalized.startsWith("#+")) {
                    normalized = "#" + normalized.substring(2);
                } else if (normalized.startsWith("+")) {
                    normalized = normalized.substring(1);
                }

                if (normalized.startsWith("#")) {
                    return new Evaluation(normalized);
                }

                if (normalized.indexOf('.') >= 0) {
                    BigDecimal pawns = new BigDecimal(normalized);
                    BigDecimal centipawns = pawns.movePointRight(2).setScale(0, RoundingMode.HALF_UP);
                    return new Evaluation(centipawns.toPlainString());
                }

                return new Evaluation(normalized);
            }

            /**
             * Used for handling chances predicate tokens (e.g.,
             * {@code chances>=W50D30L20}).
             *
             * @param b Used for receiving the predicate.
             * @return Used for indicating whether a token was matched.
             */
            private boolean handleChances(Filter.Builder b) {
                if (!peek(DslLiterals.PRED_CHANCES)) {
                    return false;
                }
                String token = readComparison();
                Filter.ComparisonOperator op = parseOp(token);
                String value = token.replaceAll("\\s", "");
                b.withChances(op, Chances.parse(value));
                return true;
            }

            /**
             * Used for skipping the informational {@code predicates=} token.
             *
             * @return Used for indicating whether a token was matched.
             */
            private boolean handlePredicatesInfo() {
                if (!peek(DslLiterals.KEY_PREDICATES)) {
                    return false;
                }
                pos += DslLiterals.KEY_PREDICATES.length();
                readToken();
                return true;
            }

            /**
             * Used for handling nested leaf blocks of the form {@code leaf[...]}.
             *
             * @param b Used for receiving the parsed child.
             * @return Used for indicating whether a token was matched.
             */
            private boolean handleLeaf(Filter.Builder b) {
                if (!peek(DslLiterals.LEAF_OPEN)) {
                    return false;
                }
                pos += DslLiterals.LEAF_OPEN.length();
                int start = pos;
                int depth = 1;
                while (depth > 0 && pos < input.length()) {
                    char c = input.charAt(pos++);
                    if (c == '[') {
                        depth++;
                    } else if (c == ']') {
                        depth--;
                    }
                }
                String sub = input.substring(start, pos - 1);
                b.addLeaf(new Parser(sub).parse());
                return true;
            }

            /** Holds the most recently parsed comparison operator token. */
            private Filter.ComparisonOperator lastOp; // set by parse*CompAndValue methods

            /**
             * Reads an int comparison token (e.g., {@code "depth>=30"}) and returns its
             * value.
             */
            private int parseIntCompAndValue() {
                String token = readComparison();
                lastOp = parseOp(token);
                String digits = token.replaceAll("[^0-9-]", "");
                return parseInt(digits);
            }

            /**
             * Reads a long comparison token (e.g., {@code "nodes>=1000000"}) and returns
             * its value.
             */
            private long parseLongCompAndValue() {
                String token = readComparison();
                lastOp = parseOp(token);
                String digits = token.replaceAll("[^0-9-]", "");
                return parseLong(digits);
            }

            /**
             * Extracts the {@link Filter.ComparisonOperator} from a token
             * by inspecting {@code >, >=, <, <=, =}.
             */
            private static Filter.ComparisonOperator parseOp(String token) {
                if (token.contains(DslLiterals.OP_SYMBOL_GREATER_EQUAL))
                    return Filter.ComparisonOperator.GREATER_EQUAL;
                if (token.contains(DslLiterals.OP_SYMBOL_GREATER))
                    return Filter.ComparisonOperator.GREATER;
                if (token.contains(DslLiterals.OP_SYMBOL_LESS_EQUAL))
                    return Filter.ComparisonOperator.LESS_EQUAL;
                if (token.contains(DslLiterals.OP_SYMBOL_LESS))
                    return Filter.ComparisonOperator.LESS;
                return Filter.ComparisonOperator.EQUAL;
            }

            /** Skips ASCII whitespace. */
            private void skipWs() {
                while (pos < input.length() && Character.isWhitespace(input.charAt(pos)))
                    pos++;
            }

            /** Skips DSL delimiters ({@code ; ,} and spaces). */
            private void skipDelims() {
                while (pos < input.length()) {
                    char c = input.charAt(pos);
                    if (c == DslLiterals.DELIM_SEMICOLON || c == DslLiterals.DELIM_COMMA
                            || c == DslLiterals.DELIM_SPACE)
                        pos++;
                    else
                        break;
                }
            }

            /**
             * @return whether the remaining input starts with {@code s} at {@link #pos}.
             */
            private boolean peek(String s) {
                return input.startsWith(s, pos);
            }

            /** Reads a comparison token until a delimiter or bracket. */
            private String readComparison() {
                return readToken();
            }

            /** Reads a generic token until a delimiter or bracket. */
            private String readToken() {
                int start = pos;
                while (pos < input.length()) {
                    char c = input.charAt(pos);
                    if (c == DslLiterals.DELIM_SEMICOLON || c == DslLiterals.DELIM_COMMA
                            || c == DslLiterals.BRACKET_OPEN || c == DslLiterals.BRACKET_CLOSE
                            || Character.isWhitespace(c))
                        break;
                    pos++;
                }
                return input.substring(start, pos);
            }

            /** Parses an {@code int} from a digit string. */
            private static int parseInt(String s) {
                return Integer.parseInt(s);
            }

            /** Parses a {@code long} from a digit string. */
            private static long parseLong(String s) {
                return Long.parseLong(s);
            }
        }
    }

    /**
     * Declarative predicate metadata used to serialize/deserialize the rule tree.
     * Each spec records the attribute kind, operator, and canonical value string.
     */
    static final class PredicateSpec {

        /**
         * Predicate attribute kinds supported by the DSL and builder.
         * Grouped into ints, longs, and complex types.
         */
        enum Kind {
            // scalar ints
            DEPTH, SELDEPTH, MULTIPV, HASHFULL,
            // scalar longs
            NODES, NPS, TBHITS, TIME,
            // complex types
            EVAL, CHANCES
        }

        /** Attribute kind this spec applies to. */
        final Kind kind;

        /** Operator applied to this attribute. */
        final ComparisonOperator op;

        /**
         * Canonical right-hand value as a string (e.g., {@code "1000000"}, {@code "0"},
         * or {@code "#3"}).
         */
        final String value; // canonical string: "1000000", "18", "0", etc.

        /**
         * Creates a new predicate spec.
         *
         * @param kind  attribute kind
         * @param op    comparison operator
         * @param value canonical value string
         */
        PredicateSpec(Kind kind, ComparisonOperator op, String value) {
            this.kind = Objects.requireNonNull(kind);
            this.op = Objects.requireNonNull(op);
            this.value = Objects.requireNonNull(value);
        }

        /**
         * Converts this spec into its DSL fragment (e.g., {@code "depth>=30"}).
         *
         * @return compact DSL token for this predicate
         */
        String toDsl() {

            final String key = switch (kind) {
                case DEPTH -> DslLiterals.PRED_DEPTH;
                case SELDEPTH -> DslLiterals.PRED_SELDEPTH;
                case MULTIPV -> DslLiterals.PRED_MULTIPV;
                case HASHFULL -> DslLiterals.PRED_HASHFULL;
                case NODES -> DslLiterals.PRED_NODES;
                case NPS -> DslLiterals.PRED_NPS;
                case TBHITS -> DslLiterals.PRED_TBHITS;
                case TIME -> DslLiterals.PRED_TIME;
                case EVAL -> DslLiterals.PRED_EVAL;
                case CHANCES -> DslLiterals.PRED_CHANCES;
            };

            final String opStr = switch (op) {
                case GREATER -> DslLiterals.OP_SYMBOL_GREATER;
                case GREATER_EQUAL -> DslLiterals.OP_SYMBOL_GREATER_EQUAL;
                case EQUAL -> DslLiterals.OP_SYMBOL_EQUAL;
                case LESS_EQUAL -> DslLiterals.OP_SYMBOL_LESS_EQUAL;
                case LESS -> DslLiterals.OP_SYMBOL_LESS;
            };

            return key + opStr + value;
        }
    }
}
