package testing;

import static testing.TestSupport.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import utility.Booleans;

/**
 * Regression checks for {@link Booleans} helper semantics.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
@SuppressWarnings("java:S2187")
public final class BooleansRegressionTest {

	/**
	 * Prevents instantiation of this utility class.
	 */
	private BooleansRegressionTest() {
		// utility
	}

	/**
	 * Runs all boolean utility regression checks.
	 *
	 * @param args ignored.
	 */
	public static void main(String[] args) {
		testArrayGates();
		testListGates();
		testInvertAndMerge();
		testBooleanArrayConversion();
		testBitGates();
		System.out.println("BooleansRegressionTest: all checks passed");
	}

	/**
	 * Verifies primitive-array boolean gates.
	 */
	private static void testArrayGates() {
		assertTrue(Booleans.and(), "empty array and");
		assertTrue(Booleans.and(true, true), "array and");
		assertFalse(Booleans.and(true, false), "array and false");
		assertTrue(Booleans.notAnd(true, false), "array notAnd");
		assertTrue(Booleans.or(false, true), "array or");
		assertTrue(Booleans.notOr(false, false), "array notOr");
		assertTrue(Booleans.xOr(false, true, false), "array xOr exactly one");
		assertFalse(Booleans.xOr(true, true, false), "array xOr multiple");
		assertFalse(Booleans.xOr(false, false), "array xOr none");
		assertTrue(Booleans.xNotOr(true, true), "array xNotOr multiple");
		assertTrue(Booleans.same(false, false), "array same false");
		assertTrue(Booleans.same(true, true), "array same true");
		assertFalse(Booleans.same(true, false), "array same mixed");
		assertTrue(Booleans.notSame(true, false), "array notSame mixed");
	}

	/**
	 * Verifies list-backed boolean gates.
	 */
	private static void testListGates() {
		List<Boolean> values = Arrays.asList(Boolean.FALSE, Boolean.TRUE, Boolean.FALSE);
		assertFalse(Booleans.and(values), "list and");
		assertTrue(Booleans.notAnd(values), "list notAnd");
		assertTrue(Booleans.or(values), "list or");
		assertFalse(Booleans.notOr(values), "list notOr");
		assertTrue(Booleans.xOr(values), "list xOr exactly one");
		assertFalse(Booleans.xNotOr(values), "list xNotOr exactly one");
		assertFalse(Booleans.same(values), "list same mixed");
		assertTrue(Booleans.notSame(values), "list notSame mixed");
	}

	/**
	 * Verifies in-place inversion and array merging.
	 */
	private static void testInvertAndMerge() {
		boolean[] inverted = Booleans.invert(true, false, false);
		assertArrayEquals(new boolean[] { false, true, true }, inverted, "array invert");

		List<Boolean> values = new ArrayList<>(Arrays.asList(Boolean.TRUE, Boolean.FALSE));
		assertSame(values, Booleans.invert(values), "list invert returns input");
		assertEquals(Arrays.asList(Boolean.FALSE, Boolean.TRUE), values, "list invert values");

		assertArrayEquals(new boolean[] { true, false, false, true },
				Booleans.merge(new boolean[] { true, false }, new boolean[] { false, true }),
				"array merge");
	}

	/**
	 * Verifies primitive bit-to-boolean conversion across the full primitive width.
	 */
	private static void testBooleanArrayConversion() {
		boolean[] byteBits = Booleans.toBooleanArray((byte) 0b1000_0001);
		assertTrue(byteBits[0], "byte bit 0");
		assertTrue(byteBits[7], "byte bit 7");
		assertFalse(byteBits[1], "byte bit 1");

		boolean[] shortBits = Booleans.toBooleanArray((short) 0x8001);
		assertTrue(shortBits[0], "short bit 0");
		assertTrue(shortBits[15], "short bit 15");

		boolean[] intBits = Booleans.toBooleanArray(Integer.MIN_VALUE | 1);
		assertTrue(intBits[0], "int bit 0");
		assertTrue(intBits[31], "int bit 31");

		boolean[] longBits = Booleans.toBooleanArray(Long.MIN_VALUE | 1L);
		assertTrue(longBits[0], "long bit 0");
		assertTrue(longBits[63], "long bit 63");
	}

	/**
	 * Verifies bit predicates for signed primitive values.
	 */
	private static void testBitGates() {
		assertTrue(Booleans.and(-1L), "long all bits set");
		assertTrue(Booleans.same(-1L), "long all bits equal set");
		assertTrue(Booleans.same(0L), "long all bits equal clear");
		assertTrue(Booleans.xOr(Long.MIN_VALUE), "long exactly one high bit");
		assertTrue(Booleans.notEqual(3L), "long mixed bits");

		assertTrue(Booleans.and(-1), "int all bits set");
		assertTrue(Booleans.same(0), "int all bits equal clear");
		assertTrue(Booleans.xOr(Integer.MIN_VALUE), "int exactly one high bit");
		assertTrue(Booleans.notEqual(3), "int mixed bits");

		assertTrue(Booleans.and((short) -1), "short all bits set");
		assertTrue(Booleans.same((short) -1), "short all bits equal set");
		assertTrue(Booleans.xOr((short) 0x4000), "short exactly one bit");
		assertTrue(Booleans.notSame((short) 3), "short mixed bits");

		assertTrue(Booleans.and((byte) -1), "byte all bits set");
		assertTrue(Booleans.same((byte) -1), "byte all bits equal set");
		assertTrue(Booleans.xOr((byte) 0x40), "byte exactly one bit");
		assertTrue(Booleans.notSame((byte) 3), "byte mixed bits");
	}
}
