package utility;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility methods for boolean algebra, mutable boolean collection helpers, and
 * bit-oriented boolean checks for primitive integer values.
 *
 * @since 2023
 * @author Lennart A. Conrad
 */
public class Booleans {

	/**
	 * This is a utility class. Do not instantiate it.
	 */
	private Booleans() {
	}

	/**
	 * Used for gathering a randomized {@code boolean}.
	 *
	 * @return a {@code boolean} with a random value.
	 */
	public static boolean randomBoolean() {
		return ThreadLocalRandom.current().nextBoolean();
	}

	/**
	 * Used for inverting the {@code Boolean} values of a given list in place.
	 *
	 * @param values the list to mutate.
	 * @return the same list with inverted values.
	 */
	public static List<Boolean> invert(List<Boolean> values) {
		for (int i = 0; i < values.size(); i++) {
			values.set(i, !values.get(i).booleanValue());
		}
		return values;
	}

	/**
	 * Used for inverting the {@code boolean} values of a given array in place.
	 *
	 * @param values the array to mutate.
	 * @return the same array with inverted values.
	 */
	public static boolean[] invert(boolean... values) {
		for (int i = 0; i < values.length; i++) {
			values[i] = !values[i];
		}
		return values;
	}

	/**
	 * Used for appending two {@code boolean} arrays.
	 *
	 * @param first  the leading values.
	 * @param second the trailing values.
	 * @return a new array containing {@code first} followed by {@code second}.
	 */
	public static boolean[] merge(boolean[] first, boolean[] second) {
		boolean[] result = new boolean[first.length + second.length];
		System.arraycopy(first, 0, result, 0, first.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	/**
	 * Used for determining if all values are {@code true}.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when every value is {@code true}.
	 */
	public static boolean and(boolean... values) {
		return trueCount(values) == values.length;
	}

	/**
	 * Used for determining if all values are {@code true}.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when every value is {@code true}.
	 */
	public static boolean and(List<Boolean> values) {
		return trueCount(values) == values.size();
	}

	/**
	 * Used for determining if not all values are {@code true}.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when at least one value is {@code false}.
	 */
	public static boolean notAnd(boolean... values) {
		return !and(values);
	}

	/**
	 * Used for determining if not all values are {@code true}.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when at least one value is {@code false}.
	 */
	public static boolean notAnd(List<Boolean> values) {
		return !and(values);
	}

	/**
	 * Used for determining if at least one value is {@code true}.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when any value is {@code true}.
	 */
	public static boolean or(boolean... values) {
		return trueCount(values) > 0;
	}

	/**
	 * Used for determining if at least one value is {@code true}.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when any value is {@code true}.
	 */
	public static boolean or(List<Boolean> values) {
		return trueCount(values) > 0;
	}

	/**
	 * Used for determining if all values are {@code false}.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when every value is {@code false}.
	 */
	public static boolean notOr(boolean... values) {
		return !or(values);
	}

	/**
	 * Used for determining if all values are {@code false}.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when every value is {@code false}.
	 */
	public static boolean notOr(List<Boolean> values) {
		return !or(values);
	}

	/**
	 * Used for determining if exactly one value is {@code true}.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when exactly one value is {@code true}.
	 */
	public static boolean xOr(boolean... values) {
		return trueCount(values) == 1;
	}

	/**
	 * Used for determining if exactly one value is {@code true}.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when exactly one value is {@code true}.
	 */
	public static boolean xOr(List<Boolean> values) {
		return trueCount(values) == 1;
	}

	/**
	 * Used for determining if exactly one value is not {@code true}.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when zero or more than one values are {@code true}.
	 */
	public static boolean xNotOr(boolean... values) {
		return !xOr(values);
	}

	/**
	 * Used for determining if exactly one value is not {@code true}.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when zero or more than one values are {@code true}.
	 */
	public static boolean xNotOr(List<Boolean> values) {
		return !xOr(values);
	}

	/**
	 * Used for determining if all values are equal.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when all values are equal.
	 */
	public static boolean same(boolean... values) {
		int count = trueCount(values);
		return count == 0 || count == values.length;
	}

	/**
	 * Used for determining if all values are equal.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when all values are equal.
	 */
	public static boolean same(List<Boolean> values) {
		int count = trueCount(values);
		return count == 0 || count == values.size();
	}

	/**
	 * Used for determining if not all values are equal.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when at least one value differs.
	 */
	public static boolean notSame(boolean... values) {
		return !same(values);
	}

	/**
	 * Used for determining if not all values are equal.
	 *
	 * @param values the values to inspect.
	 * @return {@code true} when at least one value differs.
	 */
	public static boolean notSame(List<Boolean> values) {
		return !same(values);
	}

	/**
	 * Used for converting a {@code long} into one boolean per bit, least
	 * significant bit first.
	 *
	 * @param number the value to convert.
	 * @return a {@code boolean} array with length 64.
	 */
	public static boolean[] toBooleanArray(long number) {
		return toBooleanArray(number, Long.SIZE);
	}

	/**
	 * Used for converting an {@code int} into one boolean per bit, least
	 * significant bit first.
	 *
	 * @param number the value to convert.
	 * @return a {@code boolean} array with length 32.
	 */
	public static boolean[] toBooleanArray(int number) {
		return toBooleanArray(number, Integer.SIZE);
	}

	/**
	 * Used for converting a {@code short} into one boolean per bit, least
	 * significant bit first.
	 *
	 * @param number the value to convert.
	 * @return a {@code boolean} array with length 16.
	 */
	public static boolean[] toBooleanArray(short number) {
		return toBooleanArray(number, Short.SIZE);
	}

	/**
	 * Used for converting a {@code byte} into one boolean per bit, least
	 * significant bit first.
	 *
	 * @param number the value to convert.
	 * @return a {@code boolean} array with length 8.
	 */
	public static boolean[] toBooleanArray(byte number) {
		return toBooleanArray(number, Byte.SIZE);
	}

	/**
	 * Used for determining if every {@code long} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when all 64 bits are set.
	 */
	public static boolean and(long number) {
		return allBitsSet(number, Long.SIZE);
	}

	/**
	 * Used for determining if not every {@code long} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when at least one bit is clear.
	 */
	public static boolean notAnd(long number) {
		return !and(number);
	}

	/**
	 * Used for determining if at least one {@code long} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when at least one bit is set.
	 */
	public static boolean or(long number) {
		return anyBitSet(number, Long.SIZE);
	}

	/**
	 * Used for determining if no {@code long} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when all bits are clear.
	 */
	public static boolean notOr(long number) {
		return !or(number);
	}

	/**
	 * Used for determining if exactly one {@code long} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when exactly one bit is set.
	 */
	public static boolean xOr(long number) {
		return oneBitSet(number, Long.SIZE);
	}

	/**
	 * Used for determining if not exactly one {@code long} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when zero or multiple bits are set.
	 */
	public static boolean xNotOr(long number) {
		return !xOr(number);
	}

	/**
	 * Used for determining if every {@code long} bit is equal.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when all bits are clear or all bits are set.
	 */
	public static boolean same(long number) {
		return allBitsEqual(number, Long.SIZE);
	}

	/**
	 * Used for determining if not every {@code long} bit is equal.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when the value contains both set and clear bits.
	 */
	public static boolean notEqual(long number) {
		return !same(number);
	}

	/**
	 * Used for determining if every {@code int} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when all 32 bits are set.
	 */
	public static boolean and(int number) {
		return allBitsSet(number, Integer.SIZE);
	}

	/**
	 * Used for determining if not every {@code int} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when at least one bit is clear.
	 */
	public static boolean notAnd(int number) {
		return !and(number);
	}

	/**
	 * Used for determining if at least one {@code int} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when at least one bit is set.
	 */
	public static boolean or(int number) {
		return anyBitSet(number, Integer.SIZE);
	}

	/**
	 * Used for determining if no {@code int} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when all bits are clear.
	 */
	public static boolean notOr(int number) {
		return !or(number);
	}

	/**
	 * Used for determining if exactly one {@code int} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when exactly one bit is set.
	 */
	public static boolean xOr(int number) {
		return oneBitSet(number, Integer.SIZE);
	}

	/**
	 * Used for determining if not exactly one {@code int} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when zero or multiple bits are set.
	 */
	public static boolean xNotOr(int number) {
		return !xOr(number);
	}

	/**
	 * Used for determining if every {@code int} bit is equal.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when all bits are clear or all bits are set.
	 */
	public static boolean same(int number) {
		return allBitsEqual(number, Integer.SIZE);
	}

	/**
	 * Used for determining if not every {@code int} bit is equal.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when the value contains both set and clear bits.
	 */
	public static boolean notEqual(int number) {
		return !same(number);
	}

	/**
	 * Used for determining if every {@code short} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when all 16 bits are set.
	 */
	public static boolean and(short number) {
		return allBitsSet(number, Short.SIZE);
	}

	/**
	 * Used for determining if not every {@code short} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when at least one bit is clear.
	 */
	public static boolean notAnd(short number) {
		return !and(number);
	}

	/**
	 * Used for determining if at least one {@code short} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when at least one bit is set.
	 */
	public static boolean or(short number) {
		return anyBitSet(number, Short.SIZE);
	}

	/**
	 * Used for determining if no {@code short} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when all bits are clear.
	 */
	public static boolean notOr(short number) {
		return !or(number);
	}

	/**
	 * Used for determining if exactly one {@code short} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when exactly one bit is set.
	 */
	public static boolean xOr(short number) {
		return oneBitSet(number, Short.SIZE);
	}

	/**
	 * Used for determining if not exactly one {@code short} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when zero or multiple bits are set.
	 */
	public static boolean xNotOr(short number) {
		return !xOr(number);
	}

	/**
	 * Used for determining if every {@code short} bit is equal.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when all bits are clear or all bits are set.
	 */
	public static boolean same(short number) {
		return allBitsEqual(number, Short.SIZE);
	}

	/**
	 * Used for determining if not every {@code short} bit is equal.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when the value contains both set and clear bits.
	 */
	public static boolean notSame(short number) {
		return !same(number);
	}

	/**
	 * Used for determining if every {@code byte} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when all 8 bits are set.
	 */
	public static boolean and(byte number) {
		return allBitsSet(number, Byte.SIZE);
	}

	/**
	 * Used for determining if not every {@code byte} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when at least one bit is clear.
	 */
	public static boolean notAnd(byte number) {
		return !and(number);
	}

	/**
	 * Used for determining if at least one {@code byte} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when at least one bit is set.
	 */
	public static boolean or(byte number) {
		return anyBitSet(number, Byte.SIZE);
	}

	/**
	 * Used for determining if no {@code byte} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when all bits are clear.
	 */
	public static boolean notOr(byte number) {
		return !or(number);
	}

	/**
	 * Used for determining if exactly one {@code byte} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when exactly one bit is set.
	 */
	public static boolean xOr(byte number) {
		return oneBitSet(number, Byte.SIZE);
	}

	/**
	 * Used for determining if not exactly one {@code byte} bit is set.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when zero or multiple bits are set.
	 */
	public static boolean xNotOr(byte number) {
		return !xOr(number);
	}

	/**
	 * Used for determining if every {@code byte} bit is equal.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when all bits are clear or all bits are set.
	 */
	public static boolean same(byte number) {
		return allBitsEqual(number, Byte.SIZE);
	}

	/**
	 * Used for determining if not every {@code byte} bit is equal.
	 *
	 * @param number the value to inspect.
	 * @return {@code true} when the value contains both set and clear bits.
	 */
	public static boolean notSame(byte number) {
		return !same(number);
	}

	/**
	 * Used for counting {@code true} values in an array.
	 *
	 * @param values the values to inspect.
	 * @return number of {@code true} values.
	 */
	private static int trueCount(boolean[] values) {
		int count = 0;
		for (boolean value : values) {
			if (value) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Used for counting {@code true} values in a list.
	 *
	 * @param values the values to inspect.
	 * @return number of {@code true} values.
	 */
	private static int trueCount(List<Boolean> values) {
		int count = 0;
		for (Boolean value : values) {
			if (value.booleanValue()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Used for converting the low {@code bits} bits of a value to booleans.
	 *
	 * @param number the value to convert.
	 * @param bits   number of low bits to inspect.
	 * @return one boolean per inspected bit, least significant bit first.
	 */
	private static boolean[] toBooleanArray(long number, int bits) {
		boolean[] result = new boolean[bits];
		for (int i = 0; i < result.length; i++) {
			result[i] = ((number >>> i) & 1L) != 0L;
		}
		return result;
	}

	/**
	 * Used for determining if all inspected bits are set.
	 *
	 * @param number the value to inspect.
	 * @param bits   number of low bits to inspect.
	 * @return {@code true} when all inspected bits are set.
	 */
	private static boolean allBitsSet(long number, int bits) {
		long mask = bitMask(bits);
		return (number & mask) == mask;
	}

	/**
	 * Used for determining if any inspected bit is set.
	 *
	 * @param number the value to inspect.
	 * @param bits   number of low bits to inspect.
	 * @return {@code true} when at least one inspected bit is set.
	 */
	private static boolean anyBitSet(long number, int bits) {
		return (number & bitMask(bits)) != 0L;
	}

	/**
	 * Used for determining if exactly one inspected bit is set.
	 *
	 * @param number the value to inspect.
	 * @param bits   number of low bits to inspect.
	 * @return {@code true} when one inspected bit is set.
	 */
	private static boolean oneBitSet(long number, int bits) {
		return Long.bitCount(number & bitMask(bits)) == 1;
	}

	/**
	 * Used for determining if all inspected bits are set or all are clear.
	 *
	 * @param number the value to inspect.
	 * @param bits   number of low bits to inspect.
	 * @return {@code true} when every inspected bit has the same value.
	 */
	private static boolean allBitsEqual(long number, int bits) {
		long mask = bitMask(bits);
		long lowBits = number & mask;
		return lowBits == 0L || lowBits == mask;
	}

	/**
	 * Used for building a low-bit mask.
	 *
	 * @param bits number of low bits to include.
	 * @return a mask with {@code bits} low bits set.
	 */
	private static long bitMask(int bits) {
		return bits == Long.SIZE ? -1L : (1L << bits) - 1L;
	}
}
