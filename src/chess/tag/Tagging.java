package chess.tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
     * Produces the full tag output for {@code position}, creating a fresh {@link Evaluator} for
     * providers that require one.
     *
     * @param position position to describe
     * @return read-only list of generated tag strings
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");
        try (Evaluator evaluator = new Evaluator()) {
            return tags(position, evaluator);
        }
    }

    /**
     * Compatibility entry point used by the UCI pipeline.
     *
     * <p>
     * Some callers provide a {@code parent} position to support taggers that need context from the
     * previous ply. The current {@code chess.tag} taggers operate on a single position, so the
     * {@code parent} is currently ignored.
     * </p>
     *
     * @param parent previous position (may be {@code null})
     * @param position position to describe (non-null)
     * @return tag strings as an array suitable for varargs APIs
     */
    public static String[] positionalTags(Position parent, Position position) {
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
