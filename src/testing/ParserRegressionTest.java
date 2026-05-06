package testing;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import chess.tag.Delta;
import chess.tag.Emitter;
import chess.tag.Sort;

/**
 * No-framework regression checks for tag parsing, emitting, sorting, and
 * semantic identity behavior.
 */
@SuppressWarnings("java:S2187")
public final class ParserRegressionTest {

    /**
     * Internal tag-line parser type.
     */
    private static final String LINE_CLASS = "chess.tag.Line";

    /**
     * Internal tag identity helper type.
     */
    private static final String IDENTITY_CLASS = "chess.tag.Identity";

    /**
     * Prevents instantiation.
     */
    private ParserRegressionTest() {
        // utility
    }

    /**
     * Runs the regression checks.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        testEmitterQuotesAndRoundTrips();
        testFamilySortOrderIncludesPlannedFamilies();
        testPlannedFamilyIdentities();
        testDeltaUsesPlannedFamilyIdentities();
        System.out.println("ParserRegressionTest: all checks passed");
    }

    /**
     * Verifies canonical field emission and parser escaping.
     */
    private static void testEmitterQuotesAndRoundTrips() {
        String tag = Emitter.tag("opening")
                .field("name", "King's Pawn Game")
                .field("note", "quote \"x\"")
                .build();
        assertEquals("OPENING: name=\"King's Pawn Game\" note=\"quote \\\"x\\\"\"", tag, "escaped tag");

        Object line = line(tag);
        assertEquals("OPENING", lineFamily(line), "family");
        assertEquals("King's Pawn Game", lineFields(line).get("name"), "name field");
        assertEquals("quote \"x\"", lineFields(line).get("note"), "note field");

        List<String> emitted = new ArrayList<>();
        Emitter.tag("pawn").field("structure", "passed").field("side", "white").field("square", "e7")
                .emit(emitted);
        assertEquals(List.of("PAWN: structure=passed side=white square=e7"), emitted, "builder emit");

        Emitter out = new Emitter();
        out.add("   ");
        out.add("meta", "to_move", "white");
        assertEquals(List.of("META: to_move=white"), out.sorted(), "emitter sorted output");
    }

    /**
     * Verifies the sorter orders every canonical family before unknown tags.
     */
    private static void testFamilySortOrderIncludesPlannedFamilies() {
        List<String> sorted = Sort.sort(List.of(
                "ZZZ: unknown=true",
                "CHECKMATE: pattern=back_rank_mate",
                "MOVE: legal=20",
                "META: to_move=white",
                "FACT: status=normal",
                "THREAT: type=promote side=white",
                "CAND: role=best move=e2e4",
                "PV: move=e2e4",
                "IDEA: plan=develop",
                "TACTIC: motif=fork",
                "PIECE: activity=pin side=white piece=bishop square=e2",
                "KING: safety=very_unsafe side=black",
                "PAWN: structure=passed side=white square=e7",
                "MATERIAL: balance=equal",
                "SPACE: side=white",
                "INITIATIVE: side=white",
                "DEVELOPMENT: side=white",
                "MOBILITY: side=white",
                "OUTPOST: side=white square=d5 piece=knight",
                "ENDGAME: type=rook",
                "OPENING: eco=B00"));

        assertPrefixOrder(sorted,
                "FACT:",
                "META:",
                "MOVE:",
                "THREAT:",
                "CAND:",
                "PV:",
                "IDEA:",
                "TACTIC:",
                "CHECKMATE:",
                "PIECE:",
                "KING:",
                "PAWN:",
                "MATERIAL:",
                "SPACE:",
                "INITIATIVE:",
                "DEVELOPMENT:",
                "MOBILITY:",
                "OUTPOST:",
                "ENDGAME:",
                "OPENING:",
                "ZZZ:");
    }

    /**
     * Verifies stable identities for the canonical move and mate families.
     */
    private static void testPlannedFamilyIdentities() {
        assertEquals("MOVE:legal", identity("MOVE: legal=20"), "MOVE count identity");
        assertEquals("MOVE:only", identity("MOVE: only=e2e4"), "MOVE only identity");
        assertEquals("CHECKMATE:pattern:back_rank_mate",
                identity("CHECKMATE: pattern=back_rank_mate"), "mate pattern identity");
        assertEquals("CHECKMATE:delivery", identity("CHECKMATE: delivery=queen"),
                "mate delivery identity");
    }

    /**
     * Verifies deltas treat move counts and mate attributes as changes, while
     * distinct mate patterns remain separate tags.
     */
    private static void testDeltaUsesPlannedFamilyIdentities() {
        Delta delta = Delta.diff(
                List.of("MOVE: legal=20", "CHECKMATE: delivery=queen", "CHECKMATE: pattern=back_rank_mate"),
                List.of("MOVE: legal=21", "CHECKMATE: delivery=rook", "CHECKMATE: pattern=smothered_mate"));

        assertEquals(2, delta.changed().size(), "changed count");
        assertChange(delta, "MOVE:legal", "MOVE: legal=20", "MOVE: legal=21");
        assertChange(delta, "CHECKMATE:delivery", "CHECKMATE: delivery=queen", "CHECKMATE: delivery=rook");
        assertEquals(List.of("CHECKMATE: pattern=smothered_mate"), delta.added(), "added pattern");
        assertEquals(List.of("CHECKMATE: pattern=back_rank_mate"), delta.removed(), "removed pattern");
    }

    /**
     * Asserts all sorted tags follow the expected prefix order.
     *
     * @param tags sorted tags
     * @param prefixes expected prefixes
     */
    private static void assertPrefixOrder(List<String> tags, String... prefixes) {
        assertEquals(prefixes.length, tags.size(), "sorted tag count");
        for (int i = 0; i < prefixes.length; i++) {
            if (!tags.get(i).startsWith(prefixes[i])) {
                throw new AssertionError("tag " + i + " expected prefix " + prefixes[i] + " but was "
                        + tags.get(i) + "\nall tags: " + tags);
            }
        }
    }

    /**
     * Asserts a delta change is present.
     *
     * @param delta delta to inspect
     * @param key expected identity key
     * @param from expected before tag
     * @param to expected after tag
     */
    private static void assertChange(Delta delta, String key, String from, String to) {
        for (Delta.Change change : delta.changed()) {
            if (key.equals(change.key) && from.equals(change.from) && to.equals(change.to)) {
                return;
            }
        }
        throw new AssertionError("missing change " + key + " from " + from + " to " + to
                + "\nactual changes: " + delta.toJson());
    }

    /**
     * Parses a tag line through the package-private parser.
     *
     * @param raw raw tag text
     * @return parsed line instance
     */
    private static Object line(String raw) {
        try {
            Class<?> type = Class.forName(LINE_CLASS);
            Constructor<?> constructor = type.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(raw);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not construct " + LINE_CLASS, ex);
        }
    }

    /**
     * Reads the parsed family from an internal line.
     *
     * @param line parsed line
     * @return family
     */
    private static String lineFamily(Object line) {
        return (String) field(line, "family");
    }

    /**
     * Reads the parsed fields from an internal line.
     *
     * @param line parsed line
     * @return fields
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> lineFields(Object line) {
        return (Map<String, String>) field(line, "fields");
    }

    /**
     * Computes the package-private identity for a raw tag line.
     *
     * @param raw raw tag line
     * @return identity key
     */
    private static String identity(String raw) {
        try {
            Class<?> lineType = Class.forName(LINE_CLASS);
            Class<?> identityType = Class.forName(IDENTITY_CLASS);
            Method method = identityType.getDeclaredMethod("identity", lineType);
            method.setAccessible(true);
            return (String) method.invoke(null, line(raw));
        } catch (InvocationTargetException ex) {
            rethrowCause(ex);
            throw new AssertionError("unreachable");
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not invoke " + IDENTITY_CLASS + ".identity", ex);
        }
    }

    /**
     * Reads a field reflectively.
     *
     * @param target target instance
     * @param name field name
     * @return field value
     */
    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not read " + name + " from " + target.getClass().getName(), ex);
        }
    }

    /**
     * Preserves the original failure thrown by a reflected method.
     *
     * @param ex invocation wrapper
     */
    private static void rethrowCause(InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new AssertionError(cause);
    }

    /**
     * Asserts equality.
     *
     * @param expected expected value
     * @param actual actual value
     * @param label assertion label
     */
    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }
}
