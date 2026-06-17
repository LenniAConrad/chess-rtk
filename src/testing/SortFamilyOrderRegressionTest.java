package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;

import java.util.List;

import chess.tag.Sort;
import chess.tag.core.Literals;

/**
 * Regression coverage for the M0 tag-family-registration unblocker.
 *
 * <p>The four dynamic families produced by the game-analysis layers —
 * {@code MOVE_EFFECT}, {@code LINE}, {@code VARIATION}, {@code GAME} — were
 * historically absent from {@link Sort}'s family table and the
 * {@link Literals} registry, so they bypassed the canonical sort/dedupe
 * pipeline (falling to the "unknown" rank bucket and producing inline
 * string literals in the emitters). This test pins the post-fix invariants
 * so a future edit cannot silently re-orphan them.</p>
 *
 * <p>Concretely:</p>
 * <ul>
 *   <li>{@link Literals} exposes the four constants with the canonical names;</li>
 *   <li>{@link Sort#sort(List)} positions each dynamic-family tag <em>before</em>
 *       any tag whose family is genuinely unknown to the table;</li>
 *   <li>{@link Sort#sort(List)} ranks the four dynamic families in the
 *       documented order: {@code MOVE_EFFECT} → {@code LINE} →
 *       {@code VARIATION} → {@code GAME};</li>
 *   <li>{@link Sort#sort(List)} leaves the four static-family head ranks
 *       (FACT, META, MOVE) unchanged so existing golden orderings keep working.</li>
 * </ul>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class SortFamilyOrderRegressionTest {

	/**
	 * Utility class; prevent instantiation.
	 */
	private SortFamilyOrderRegressionTest() {
		// utility
	}

	/**
	 * Runs every regression check and prints a success line.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		testLiteralsExposeCanonicalFamilyNames();
		testDynamicFamiliesRankBeforeUnknownTags();
		testDynamicFamiliesAppearInDocumentedOrder();
		testStaticFamilyHeadOrderIsPreserved();
		System.out.println("SortFamilyOrderRegressionTest: all checks passed");
	}

	/**
	 * Verifies the canonical family-name constants are present in {@link Literals}.
	 */
	private static void testLiteralsExposeCanonicalFamilyNames() {
		assertEquals("MOVE_EFFECT", Literals.MOVE_EFFECT, "Literals.MOVE_EFFECT canonical name");
		assertEquals("LINE", Literals.LINE, "Literals.LINE canonical name");
		assertEquals("VARIATION", Literals.VARIATION, "Literals.VARIATION canonical name");
		assertEquals("GAME", Literals.GAME, "Literals.GAME canonical name");
	}

	/**
	 * Verifies dynamic-family tags rank before an entry from an unknown family.
	 *
	 * <p>An "unknown" family lands at the fallback rank (one past the table)
	 * and so should sort last. Before the fix the four dynamic families fell
	 * into that same bucket; this assertion ensures none still does.</p>
	 */
	private static void testDynamicFamiliesRankBeforeUnknownTags() {
		List<String> sorted = Sort.sort(List.of(
				"BOGUS_FAMILY: x=1",
				"MOVE_EFFECT: san=Rd8# type=checkmate",
				"LINE: motif=combination length=2 line=\"a b\"",
				"VARIATION: branch_ply=0 length=1 line=\"a\"",
				"GAME: result_cause=checkmate"));
		assertTrue(sorted.indexOf("MOVE_EFFECT: san=Rd8# type=checkmate")
				< sorted.indexOf("BOGUS_FAMILY: x=1"),
				"MOVE_EFFECT precedes BOGUS_FAMILY");
		assertTrue(sorted.indexOf("LINE: motif=combination length=2 line=\"a b\"")
				< sorted.indexOf("BOGUS_FAMILY: x=1"),
				"LINE precedes BOGUS_FAMILY");
		assertTrue(sorted.indexOf("VARIATION: branch_ply=0 length=1 line=\"a\"")
				< sorted.indexOf("BOGUS_FAMILY: x=1"),
				"VARIATION precedes BOGUS_FAMILY");
		assertTrue(sorted.indexOf("GAME: result_cause=checkmate")
				< sorted.indexOf("BOGUS_FAMILY: x=1"),
				"GAME precedes BOGUS_FAMILY");
	}

	/**
	 * Verifies the four dynamic families sort in the documented MOVE_EFFECT →
	 * LINE → VARIATION → GAME order.
	 */
	private static void testDynamicFamiliesAppearInDocumentedOrder() {
		List<String> sorted = Sort.sort(List.of(
				"GAME: result_cause=checkmate",
				"VARIATION: branch_ply=0 length=1 line=\"a\"",
				"LINE: motif=forcing count=2 line=\"a b\"",
				"MOVE_EFFECT: san=Rd8# type=checkmate"));
		int moveEffectIdx = sorted.indexOf("MOVE_EFFECT: san=Rd8# type=checkmate");
		int lineIdx = sorted.indexOf("LINE: motif=forcing count=2 line=\"a b\"");
		int variationIdx = sorted.indexOf("VARIATION: branch_ply=0 length=1 line=\"a\"");
		int gameIdx = sorted.indexOf("GAME: result_cause=checkmate");
		assertTrue(moveEffectIdx < lineIdx, "MOVE_EFFECT precedes LINE");
		assertTrue(lineIdx < variationIdx, "LINE precedes VARIATION");
		assertTrue(variationIdx < gameIdx, "VARIATION precedes GAME");
	}

	/**
	 * Verifies the existing static-family head order (FACT, META, MOVE) is
	 * preserved so existing golden orderings keep working.
	 */
	private static void testStaticFamilyHeadOrderIsPreserved() {
		List<String> sorted = Sort.sort(List.of(
				"GAME: result_cause=checkmate",
				"MOVE: type=quiet",
				"META: eco=B12",
				"FACT: side=white",
				"MOVE_EFFECT: san=Rd8# type=checkmate"));
		assertTrue(sorted.indexOf("FACT: side=white")
				< sorted.indexOf("META: eco=B12"),
				"FACT still precedes META");
		assertTrue(sorted.indexOf("META: eco=B12")
				< sorted.indexOf("MOVE: type=quiet"),
				"META still precedes MOVE");
		assertTrue(sorted.indexOf("MOVE: type=quiet")
				< sorted.indexOf("MOVE_EFFECT: san=Rd8# type=checkmate"),
				"static MOVE precedes dynamic MOVE_EFFECT");
		assertTrue(sorted.indexOf("MOVE_EFFECT: san=Rd8# type=checkmate")
				< sorted.indexOf("GAME: result_cause=checkmate"),
				"MOVE_EFFECT precedes GAME among dynamic families");
	}
}
