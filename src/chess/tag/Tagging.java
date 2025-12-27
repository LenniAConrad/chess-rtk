package chess.tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import chess.core.Position;
import chess.eval.Evaluator;

/**
 * Central orchestrator for the {@code chess.tag} tagging subsystem.
 *
 * <p>
 * {@code Tagx} maintains an ordered set of {@link TagProvider}s that produce textual
 * descriptions of various position facets (piece placements, immediate attacks, etc.).
 * Calling {@link #tags(Position)} produces an immutable mix of those strings that downstream
 * consumers can reason over or present verbatim. All providers share a single {@link Evaluator}
 * instance when one is available, ensuring expensive evaluation resources are reused consistently.
 * </p>
 *
 * <p>
 * The ordering inside {@link #PROVIDERS} is deterministic and defines the sequence of emitted tags.
 * To add a new tag generator, simply implement {@link TagProvider} and register it here.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Tagging {

    /**
     * Shared evaluator instance reused across tag runs.
     *
     * <p>
     * Tagging can be invoked from hot paths such as puzzle mining where creating and tearing down
     * native evaluation resources (LC0 CPU threads / CUDA device memory) per position is
     * prohibitively expensive. A single shared evaluator keeps those resources alive for the
     * duration of the process and is closed via a shutdown hook.
     * </p>
     */
    private static final class SharedEvaluator {
        /**
         * Reference holding the shared evaluator instance.
         */
        private static final AtomicReference<Evaluator> INSTANCE = new AtomicReference<>();

        /**
         * Tracks whether the shutdown hook has been installed.
         */
        private static final AtomicBoolean SHUTDOWN_HOOK_INSTALLED = new AtomicBoolean(false);

        /**
         * Prevents instantiation of this utility class.
         */
        private SharedEvaluator() {
            // utility
        }

        /**
         * Returns the shared evaluator instance, creating it on first use.
         *
         * <p>
         * The returned evaluator is owned by the JVM process and will be closed via a shutdown hook.
         * </p>
         *
         * @return shared evaluator instance (non-null)
         */
        static Evaluator get() {
            Evaluator existing = INSTANCE.get();
            if (existing != null) {
                return existing;
            }

            Evaluator created = new Evaluator();
            if (INSTANCE.compareAndSet(null, created)) {
                installShutdownHook();
                return created;
            }

            try {
                created.close();
            } catch (Exception ignore) {
                // best-effort cleanup
            }
            return INSTANCE.get();
        }

        /**
         * Installs a shutdown hook that closes the shared evaluator instance.
         */
        private static void installShutdownHook() {
            if (!SHUTDOWN_HOOK_INSTALLED.compareAndSet(false, true)) {
                return;
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Evaluator shared = INSTANCE.get();
                if (shared != null) {
                    try {
                        shared.close();
                    } catch (Exception ignore) {
                        // best-effort cleanup
                    }
                }
            }, "ucicli-tagging-evaluator-shutdown"));
        }
    }

    /**
     * Immutable list of tag providers that contribute to every tagx run.
     */
    private static final List<TagProvider> PROVIDERS = List.of(
            new DifficultyProvider(),
            new PieceAblationProvider(),
            new AttackProvider(),
            new MoveTagProvider()
    );

    /**
     * Prevents instantiation; this class exposes only static helpers.
     */
    private Tagging() {
        // utility class
    }

    /**
     * Produces the full tag output for {@code position} using a shared {@link Evaluator} instance.
     *
     * <p>
     * This avoids repeatedly loading LC0 weights (and repeatedly allocating native resources) in
     * hot paths such as mining, while still allowing callers to supply an explicit evaluator via
     * {@link #tags(Position, Evaluator)}.
     * </p>
     *
     * @param position position to describe
     * @return read-only list of generated tag strings
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");
        return tags(position, SharedEvaluator.get());
    }

    /**
     * Compatibility entry point used by the UCI pipeline.
     *
     * @param position position to describe (non-null)
     * @return tag strings as an array suitable for varargs APIs
     */
    public static String[] positionalTags(Position position) {
        List<String> tags = tags(position);
        return tags.toArray(new String[0]);
    }

    /**
     * Produces the tag output for {@code position} while reusing an existing evaluator instance.
     *
     * @param position position to describe
     * @param evaluator evaluator shared across providers
     * @return read-only list of generated tag strings
     */
    public static List<String> tags(Position position, Evaluator evaluator) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(evaluator, "evaluator");

        List<String> all = new ArrayList<>();
        for (TagProvider provider : PROVIDERS) {
            List<String> tags = provider.tags(position, evaluator);
            if (tags != null && !tags.isEmpty()) {
                all.addAll(tags);
            }
        }
        return Collections.unmodifiableList(all);
    }

    /**
 * Contract for composable tag generators that plug into {@code Tagging}.
     */
    public interface TagProvider {

        /**
         * Emits zero or more tag strings for the supplied {@code position}.
         *
         * @param position position to inspect
         * @param evaluator evaluator that can be reused if needed
         * @return list of tag strings, or an empty list if nothing applies
         */
        List<String> tags(Position position, Evaluator evaluator);
    }

    /**
     * Provider wrapper around {@link PieceAblationTagger}.
     */
    private static final class PieceAblationProvider implements TagProvider {

        /**
         * Delegates tagging to {@link PieceAblationTagger#tag(Position, Evaluator, boolean)} and
         * requests color-qualified output.
         *
         * @param position position to tag
         * @param evaluator evaluator instance to reuse
         * @return piece placement tags
         */
        @Override
        public List<String> tags(Position position, Evaluator evaluator) {
            return PieceAblationTagger.tag(position, evaluator, true);
        }
    }

    /**
     * Provider wrapper around {@link Difficulty}.
     */
    private static final class DifficultyProvider implements TagProvider {

        /**
         * Emits a coarse difficulty tag based on the position evaluation.
         *
         * @param position position to tag
         * @param evaluator evaluator instance to reuse
         * @return difficulty tag
         */
        @Override
        public List<String> tags(Position position, Evaluator evaluator) {
            return Difficulty.tags(position, evaluator);
        }
    }

    /**
     * Provider wrapper around {@link Attack}.
     */
    private static final class AttackProvider implements TagProvider {

        /**
         * Emits the attack/fork strings produced by {@link Attack#tag(Position)}.
         *
         * @param position position to tag
         * @param evaluator evaluator instance (unused but accepted for consistency)
         * @return attack description tags
         */
        @Override
        public List<String> tags(Position position, Evaluator evaluator) {
            return Attack.tag(position);
        }
    }

    /**
     * Provider wrapper around {@link MoveTagger}.
     */
    private static final class MoveTagProvider implements TagProvider {

        /**
         * Emits tags for all legal moves from the supplied position.
         *
         * @param position position to tag
         * @param evaluator evaluator instance (unused but accepted for consistency)
         * @return move outcome tags
         */
        @Override
        public List<String> tags(Position position, Evaluator evaluator) {
            return MoveTagger.tags(position, position.getMoves());
        }
    }
}
