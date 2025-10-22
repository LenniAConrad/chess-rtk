package utility;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Used for {@code boolean} algebra. Contains 'And', 'NotAnd', 'Or', 'NotOr',
 * 'XOr', 'XNotOr', 'Equal' and 'NotEqual' methods. Also simple boolean
 * utility-methods are made available here.
 * 
 * <p>
 * Logic table with all gates, where '0' represents {@code false} and '1'
 * represents {@code true}:
 * </p>
 * 
 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
 * </style>
 * 
 * <pre>
 * 	<table border=1>
 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> {@link #and(boolean...) And} </th> <th> {@link #notAnd(boolean...) NotAnd} </th> <th> {@link #or(boolean...) Or} </th> <th> {@link #notOr(boolean...) NotOr} </th> <th> {@link #xOr(boolean...) XOr} </th> <th> {@link #xNotOr(boolean...) XNotOr} </th> <th> {@link #same(boolean...) Equal} </th> <th> {@link #NOT_SAME} </th> </tr>
 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
 * 	</table>
 * </pre>
 * 
 * @since 2023
 * @author Lennart A. Conrad
 */
public class Booleans {

	/**
	 * This is a utility Class. Don't let anyone instantiate it.
	 */
	private Booleans() {

	}

	/**
	 * Used for gathering a randomized {@code boolean}.
	 * 
	 * @return A {@code boolean} with a random value.
	 */
	public static boolean randomBoolean() {
		return ThreadLocalRandom.current().nextBoolean();
	}

	/**
	 * Used for inverting the {@code boolean} values of a given array. Will return
	 * the same array, in which all {@code false} values have been converted to
	 * {@code true} and all {@code true} values have been converted to
	 * {@code false}.
	 * 
	 * @param values
	 * @return The same list with inverted {@code boolean} values.
	 */
	public static List<Boolean> invert(List<Boolean> values) {
		for (int i = 0; i < values.size(); i++) {
			values.set(i, !values.get(i).booleanValue());
		}
		return values;
	}

	/**
	 * Used for inverting the {@code boolean} values of a given list. Will return
	 * the same list, in which all {@code false} values have been converted to
	 * {@code true} and all {@code true} values have been converted to
	 * {@code false}.
	 * 
	 * @param values
	 * @return The same list with inverted {@code boolean} values.
	 */
	public static boolean[] invert(boolean... values) {
		for (int i = 0; i < values.length; i++) {
			values[i] = !values[i];
		}
		return values;
	}

	/**
	 * Used for appending two {@code boolean} arrays. Will return a new array
	 * consisting out of all the values from the first, followed by all the values
	 * from the second.
	 * 
	 * @param first
	 * @param second
	 * @return A new array of {@code boolean} consisting out of the two input
	 *         arrays.
	 */
	public static boolean[] merge(boolean[] first, boolean[] second) {
		boolean[] result = new boolean[first.length + second.length];
		System.arraycopy(first, 0, result, 0, first.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	/**
	 * Used for determining if all values in a given array of {@code boolean} are
	 * {@code true}.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> And </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * 
	 * @param values
	 * @return If all values are {@code true}, the method will return {@code true}
	 *         else {@code false}.
	 * @see #notAnd(boolean...)
	 * @see #or(boolean...)
	 * @see #notOr(boolean...)
	 * @see #xOr(boolean...)
	 * @see #xNotOr(boolean...)
	 * @see #same(boolean...)
	 * @see #notSame(boolean...)
	 */
	public static boolean and(boolean... values) {
		for (int i = 0; i < values.length; i++) {
			if (!values[i]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Used for determining if all values in a given list of {@code boolean} are
	 * {@code true}.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> And </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * 
	 * @param values
	 * @return If all values are {@code true}, the method will return {@code true}
	 *         else {@code false}.
	 * @see #notAnd(List)
	 * @see #or(List)
	 * @see #notOr(List)
	 * @see #xOr(List)
	 * @see #xNotOr(List)
	 * @see #same(List)
	 * @see #notSame(List)
	 */
	public static boolean and(List<Boolean> values) {
		for (int i = 0; i < values.size(); i++) {
			if (!values.get(i).booleanValue()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Used for determining if not all values in a given array of {@code boolean}
	 * are {@code true}.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> NotAnd </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If not all values are {@code true}, the method will return
	 *         {@code true} else {@code false}.
	 * @see #and(boolean...)
	 * @see #or(boolean...)
	 * @see #notOr(boolean...)
	 * @see #xOr(boolean...)
	 * @see #xNotOr(boolean...)
	 * @see #same(boolean...)
	 * @see #notSame(boolean...)
	 */
	public static boolean notAnd(boolean... values) {
		return !and(values);
	}

	/**
	 * Used for determining if not all values in a given list of {@code boolean} are
	 * {@code true}.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> NotAnd </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If not all values are {@code true}, the method will return
	 *         {@code true} else {@code false}.
	 * @see #and(List)
	 * @see #or(List)
	 * @see #notOr(List)
	 * @see #xOr(List)
	 * @see #xNotOr(List)
	 * @see #same(List)
	 * @see #notSame(List)
	 */
	public static boolean notAnd(List<Boolean> values) {
		return !and(values);
	}

	/**
	 * Used for determining if at least one value in a given array of
	 * {@code boolean} is {@code true}.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> Or </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If at least one value is {@code true}, the method will return
	 *         {@code true} else {@code false}.
	 * @see #and(boolean...)
	 * @see #notAnd(boolean...)
	 * @see #notOr(boolean...)
	 * @see #xOr(boolean...)
	 * @see #xNotOr(boolean...)
	 * @see #same(boolean...)
	 * @see #notSame(boolean...)
	 */
	public static boolean or(boolean... values) {
		for (int i = 0; i < values.length; i++) {
			if (values[i]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Used for determining if at least one value in a given list of {@code boolean}
	 * is {@code true}.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> Or </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If at least one value is {@code true}, the method will return
	 *         {@code true} else {@code false}.
	 * @see #and(List)
	 * @see #notAnd(List)
	 * @see #notOr(List)
	 * @see #xOr(List)
	 * @see #xNotOr(List)
	 * @see #same(List)
	 * @see #notSame(List)
	 */
	public static boolean or(List<Boolean> values) {
		for (int i = 0; i < values.size(); i++) {
			if (values.get(i).booleanValue()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Used for determining if all values in a given array of {@code boolean} are
	 * {@code false} or not.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> NotOr </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If all values are {@code false} the method will return {@code true}
	 *         else {@code false}.
	 * @see #and(boolean...)
	 * @see #notAnd(boolean...)
	 * @see #or(boolean...)
	 * @see #xOr(boolean...)
	 * @see #xNotOr(boolean...)
	 * @see #same(boolean...)
	 * @see #notSame(boolean...)
	 */
	public static boolean notOr(boolean... values) {
		return !or(values);
	}

	/**
	 * Used for determining if all values in a given list of {@code boolean} are
	 * {@code false} or not.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> NotOr </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If all values are {@code false} the method will return {@code true}
	 *         else {@code false}.
	 * @see #and(List)
	 * @see #notAnd(List)
	 * @see #or(List)
	 * @see #xOr(List)
	 * @see #xNotOr(List)
	 * @see #same(List)
	 * @see #notSame(List)
	 */
	public static boolean notOr(List<Boolean> values) {
		return !or(values);
	}

	/**
	 * Used for determining if only one value in a given array of {@code boolean} is
	 * true.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> XOr </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If only one value is {@code true}, the method will return
	 *         {@code true} else {@code false}.
	 * @see #and(boolean...)
	 * @see #notAnd(boolean...)
	 * @see #or(boolean...)
	 * @see #notOr(boolean...)
	 * @see #xNotOr(boolean...)
	 * @see #same(boolean...)
	 * @see #notSame(boolean...)
	 */
	public static boolean xOr(boolean... values) {
		boolean found = false;
		for (int i = 1; i < values.length; i++) {
			if (values[i]) {
				if (found) {
					return false;
				}
				found = true;
			}
		}
		return true;
	}

	/**
	 * Used for determining if only one value in a given list of {@code boolean} is
	 * true.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> XOr </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * 
	 * @param values
	 * @return If only one value is {@code true}, the method will return
	 *         {@code true} else {@code false}.
	 * @see #and(List)
	 * @see #notAnd(List)
	 * @see #or(List)
	 * @see #notOr(List)
	 * @see #xNotOr(List)
	 * @see #same(List)
	 * @see #notSame(List)
	 */
	public static boolean xOr(List<Boolean> values) {
		boolean found = false;
		for (int i = 1; i < values.size(); i++) {
			if (values.get(i).booleanValue()) {
				if (found) {
					return false;
				}
				found = true;
			}
		}
		return true;
	}

	/**
	 * Used for determining if not just one value in a given array of
	 * {@code boolean} is {@code true}.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> XNotOr </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If not just one value is {@code true}, the method will return
	 *         {@code true} else {@code false}.
	 * @see #and(boolean...)
	 * @see #notAnd(boolean...)
	 * @see #or(boolean...)
	 * @see #notOr(boolean...)
	 * @see #xOr(boolean...)
	 * @see #same(boolean...)
	 * @see #notSame(boolean...)
	 */
	public static boolean xNotOr(boolean... values) {
		return !xOr(values);
	}

	/**
	 * Used for determining if not just one value in a given list of {@code boolean}
	 * is {@code true}.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> XNotOr </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If not just one value is {@code true}, the method will return
	 *         {@code true} else {@code false}.
	 * @see #and(List)
	 * @see #notAnd(List)
	 * @see #or(List)
	 * @see #notOr(List)
	 * @see #xOr(List)
	 * @see #same(List)
	 * @see #notSame(List)
	 */
	public static boolean xNotOr(List<Boolean> values) {
		return !xOr(values);
	}

	/**
	 * Used for determining if all values in a given array of {@code boolean} are
	 * equal.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> Equal </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If all values are equal, the method will return {@code true} else
	 *         {@code false}.
	 * @see #and(boolean...)
	 * @see #notAnd(boolean...)
	 * @see #or(boolean...)
	 * @see #notOr(boolean...)
	 * @see #xNotOr(boolean...)
	 * @see #same(boolean...)
	 * @see #notSame(boolean...)
	 */
	public static boolean same(boolean... values) {
		for (int i = 1; i < values.length; i++) {
			if (values[i] != values[i - 1]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Used for determining if all values in a given list of {@code boolean} are
	 * equal.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> Equal </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If all values are equal, the method will return {@code true} else
	 *         {@code false}.
	 * @see #and(List)
	 * @see #notAnd(List)
	 * @see #or(List)
	 * @see #notOr(List)
	 * @see #xOr(List)
	 * @see #xNotOr(List)
	 * @see #notSame(List)
	 */
	public static boolean same(List<Boolean> values) {
		for (int i = 1; i < values.size(); i++) {
			if (values.get(i).booleanValue() != values.get(i - 1).booleanValue()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Used for determining if all values in a given array of {@code boolean} are
	 * not equal.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> NotEqual </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If all values are not equal, the method will return {@code true} else
	 *         {@code false}.
	 * @see #and(boolean...)
	 * @see #notAnd(boolean...)
	 * @see #or(boolean...)
	 * @see #notOr(boolean...)
	 * @see #xNotOr(boolean...)
	 * @see #same(boolean...)
	 * @see #notSame(boolean...)
	 */
	public static boolean notSame(boolean... values) {
		return !same(values);
	}

	/**
	 * Used for determining if all values in a given list of {@code boolean} are not
	 * equal.
	 * 
	 * <p>
	 * Logic table, where '0' represents {@code false} and '1' represents
	 * {@code true}:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> A </th> <th> B </th> <th> C </th> <th> NotEqual </th> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 1 </td> <td> 0 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 1 </td> <td> 0 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 1 </td> <td> 0 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 1 </td> <td> 1 </td> </tr>
	 * 		<tr> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> </tr>
	 * 	</table>
	 * </pre>
	 * 
	 * @param values
	 * @return If all values are not equal, the method will return {@code true} else
	 *         {@code false}.
	 * @see #and(List)
	 * @see #notAnd(List)
	 * @see #or(List)
	 * @see #notOr(List)
	 * @see #xOr(List)
	 * @see #xNotOr(List)
	 * @see #same(List)
	 */
	public static boolean notSame(List<Boolean> values) {
		return !same(values);
	}

	/**
	 * Used for converting a {@code Long} into a {@code Boolean} array. The
	 * {@code Boolean} array will start from the rightmost to the leftmost
	 * <i>bit</i>, from <i>bit</i><sub>0</sub> to <i>bit</i><sub>63</sub>.
	 * 
	 * <p>
	 * Example:
	 * </p>
	 * 
	 * <blockquote> {@code Long}
	 * {@code 0b101000100100101011011111010110101101000100001011011101101100000L}
	 * will be turned into {@code false}, {@code false}, {@code false},
	 * {@code false}, {@code false}, {@code true}, {@code true}, {@code false},
	 * {@code true}, {@code true}, {@code false}, {@code true}, {@code true},
	 * {@code true}, {@code false}, {@code true}, {@code true}, {@code false},
	 * {@code true}, {@code false}, {@code false}, {@code false}, {@code false},
	 * {@code true}, {@code false}, {@code false}, {@code false}, {@code true},
	 * {@code false}, {@code true}, {@code true}, {@code true}, {@code false},
	 * {@code false}, {@code false}, {@code false}, {@code false}, {@code true},
	 * {@code true}, {@code false}, {@code true}, {@code true}, {@code false},
	 * {@code true}, {@code true}, {@code true}, {@code false}, {@code true},
	 * {@code true}, {@code false}, {@code true}, {@code false}, {@code false},
	 * {@code false}, {@code false}, {@code true}, {@code false}, {@code false},
	 * {@code false}, {@code true}, {@code false}, {@code true}, {@code true},
	 * {@code true}. </blockquote>
	 * 
	 * @param number
	 * @return A {@code Boolean} array with the length of 64 that consists out of
	 *         the bit values of the {@code Long}
	 */
	public static boolean[] toBooleanArray(long number) {
		boolean[] result = new boolean[Long.SIZE];
		for (int i = 0; i < result.length; i++) {
			result[i] = ((number & 0xFF) & (1 << i)) != 0;
		}
		return result;
	}

	/**
	 * Used for converting a {@code Integer} into a {@code Boolean} array. The
	 * {@code Boolean} array will start from the rightmost to the leftmost
	 * <i>bit</i>, from <i>bit</i><sub>0</sub> to <i>bit</i><sub>31</sub>.
	 * 
	 * <p>
	 * Example:
	 * </p>
	 * 
	 * <blockquote> {@code Integer} {@code 0b1101011110110111001010011101110} will
	 * be turned into {@code false}, {@code true}, {@code true}, {@code true},
	 * {@code false}, {@code true}, {@code true}, {@code true}, {@code false},
	 * {@code false}, {@code true}, {@code false}, {@code true}, {@code false},
	 * {@code false}, {@code true}, {@code true}, {@code true}, {@code false},
	 * {@code true}, {@code true}, {@code false}, {@code true}, {@code true},
	 * {@code true}, {@code true}, {@code false}, {@code true}, {@code false},
	 * {@code true}, {@code true}, {@code false}. </blockquote>
	 * 
	 * @param number
	 * @return A {@code Boolean} array with the length of 32 that consists out of
	 *         the bit values of the {@code Integer}
	 */
	public static boolean[] toBooleanArray(int number) {
		boolean[] result = new boolean[Integer.SIZE];
		for (int i = 0; i < result.length; i++) {
			result[i] = ((number & 0xFF) & (1 << i)) != 0;
		}
		return result;
	}

	/**
	 * Used for converting a {@code Short} into a {@code Boolean} array. The
	 * {@code Boolean} array will start from the rightmost to the leftmost
	 * <i>bit</i>, from <i>bit</i><sub>0</sub> to <i>bit</i><sub>15</sub>.
	 * 
	 * <p>
	 * Example:
	 * </p>
	 * 
	 * <blockquote> {@code Short} {@code 0b1100001111001101} will be turned into
	 * {@code true}, {@code false}, {@code true}, {@code true}, {@code false},
	 * {@code false}, {@code true}, {@code true}, {@code true}, {@code true},
	 * {@code false}, {@code false}, {@code false}, {@code false}, {@code true},
	 * {@code true}. </blockquote>
	 * 
	 * @param number
	 * @return A {@code Boolean} array with the length of 16 that consists out of
	 *         the bit values of the {@code Short}
	 */
	public static boolean[] toBooleanArray(short number) {
		boolean[] result = new boolean[Short.SIZE];
		for (int i = 0; i < result.length; i++) {
			result[i] = ((number & 0xFF) & (1 << i)) != 0;
		}
		return result;
	}

	/**
	 * Used for converting a {@code Byte} into a {@code Boolean} array. The
	 * {@code Boolean} array will start from the rightmost to the leftmost
	 * <i>bit</i>, from <i>bit</i><sub>0</sub> to <i>bit</i><sub>7</sub>.
	 * 
	 * <p>
	 * Example:
	 * </p>
	 * 
	 * <blockquote> {@code Byte} {@code 0b10001101} will be turned into
	 * {@code true}, {@code false}, {@code true}, {@code true}, {@code false},
	 * {@code false}, {@code false}, {@code true}. </blockquote>
	 * 
	 * @param number
	 * @return A {@code Boolean} array with the length of 8 that consists out of the
	 *         bit values of the {@code Byte}
	 */
	public static boolean[] toBooleanArray(byte number) {
		boolean[] result = new boolean[Byte.SIZE];
		for (int i = 0; i < result.length; i++) {
			result[i] = ((number & 0xFF) & (1 << i)) != 0;
		}
		return result;
	}

	/**
	 * Used for determining if every <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If every <i>bit</i> is set
	 * @see #notAnd(long)
	 * @see #or(long)
	 * @see #notOr(long)
	 * @see #xOr(long)
	 * @see #xNotOr(long)
	 * @see #same(long)
	 * @see #notSame(long)
	 */
	public static boolean and(long number) {
		return number == 0b11111111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L;
	}

	/**
	 * Used for determining if not every <i>bit</i> set.
	 * 
	 * @param number
	 * @return If not every <i>bit</i> is set
	 * @see #and(long)
	 * @see #or(long)
	 * @see #notOr(long)
	 * @see #xOr(long)
	 * @see #xNotOr(long)
	 * @see #same(long)
	 * @see #notSame(long)
	 */
	public static boolean notAnd(long number) {
		return !and(number);
	}

	/**
	 * Used for determining if at least one <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If at least one <i>bit</i> is set
	 * @see #and(long)
	 * @see #notAnd(long)
	 * @see #notOr(long)
	 * @see #xOr(long)
	 * @see #xNotOr(long)
	 * @see #same(long)
	 * @see #notSame(long)
	 */
	public static boolean or(long number) {
		return number != 0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L;
	}

	/**
	 * Used for determining if no <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If no <i>bit</i> is set
	 * @see #and(long)
	 * @see #notAnd(long)
	 * @see #or(long)
	 * @see #xOr(long)
	 * @see #xNotOr(long)
	 * @see #same(long)
	 * @see #notSame(long)
	 */
	public static boolean notOr(long number) {
		return !or(number);
	}

	/**
	 * Used for determining if exactly one <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If exactly one <i>bit</i> is set
	 * @see #and(long)
	 * @see #notAnd(long)
	 * @see #or(long)
	 * @see #notOr(long)
	 * @see #xNotOr(long)
	 * @see #same(long)
	 * @see #notSame(long)
	 */
	public static boolean xOr(long number) {
		return number != 0 && (number & (number - 1)) == 0;
	}

	/**
	 * Used for determining if not just one <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If not just one <i>bit</i> is set
	 * @see #and(long)
	 * @see #notAnd(long)
	 * @see #or(long)
	 * @see #notOr(long)
	 * @see #xOr(long)
	 * @see #same(long)
	 * @see #notSame(long)
	 */
	public static boolean xNotOr(long number) {
		return !xOr(number);
	}

	/**
	 * Used for determining if every <i>bit</i> is equal to every other <i>bit</i>.
	 * 
	 * @param number
	 * @return If every <i>bit</i> is equal to every other <i>bit</i>
	 * @see #and(long)
	 * @see #notAnd(long)
	 * @see #or(long)
	 * @see #notOr(long)
	 * @see #xOr(long)
	 * @see #xNotOr(long)
	 * @see #notSame(long)
	 */
	public static boolean same(long number) {
		if (number == 0b11111111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L) {
			return true;
		}
		return number == 0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L;
	}

	/**
	 * Used for determining if every <i>bit</i> is not equal to every other
	 * <i>bit</i>.
	 * 
	 * @param number
	 * @return If every <i>bit</i> is not equal to every other <i>bit</i>
	 * @see #and(long)
	 * @see #notAnd(long)
	 * @see #or(long)
	 * @see #notOr(long)
	 * @see #xOr(long)
	 * @see #xNotOr(long)
	 * @see #same(long)
	 */
	public static boolean notEqual(long number) {
		return !same(number);
	}

	/**
	 * Used for determining if every <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If every <i>bit</i> is set
	 * @see #notAnd(int)
	 * @see #or(int)
	 * @see #notOr(int)
	 * @see #xOr(int)
	 * @see #xNotOr(int)
	 * @see #same(int)
	 * @see #notSame(int)
	 */
	public static boolean and(int number) {
		return number == 0b11111111_11111111_11111111_11111111;
	}

	/**
	 * Used for determining if not every <i>bit</i> set.
	 * 
	 * @param number
	 * @return If not every <i>bit</i> is set
	 * @see #and(int)
	 * @see #or(int)
	 * @see #notOr(int)
	 * @see #xOr(int)
	 * @see #xNotOr(int)
	 * @see #same(int)
	 * @see #notSame(int)
	 */
	public static boolean notAnd(int number) {
		return !and(number);
	}

	/**
	 * Used for determining if at least one <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If at least one <i>bit</i> is set
	 * @see #and(int)
	 * @see #notAnd(int)
	 * @see #notOr(int)
	 * @see #xOr(int)
	 * @see #xNotOr(int)
	 * @see #same(int)
	 * @see #notSame(int)
	 */
	public static boolean or(int number) {
		return number != 0b00000000_00000000_00000000_00000000;
	}

	/**
	 * Used for determining if no <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If no <i>bit</i> is set
	 * @see #and(int)
	 * @see #notAnd(int)
	 * @see #or(int)
	 * @see #xOr(int)
	 * @see #xNotOr(int)
	 * @see #same(int)
	 * @see #notSame(int)
	 */
	public static boolean notOr(int number) {
		return !or(number);
	}

	/**
	 * Used for determining if exactly one <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If exactly one <i>bit</i> is set
	 * @see #and(int)
	 * @see #notAnd(int)
	 * @see #or(int)
	 * @see #notOr(int)
	 * @see #xNotOr(int)
	 * @see #same(int)
	 * @see #notSame(int)
	 */
	public static boolean xOr(int number) {
		return number != 0 && (number & (number - 1)) == 0;
	}

	/**
	 * Used for determining if not just one <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If not just one <i>bit</i> is set
	 * @see #and(int)
	 * @see #notAnd(int)
	 * @see #or(int)
	 * @see #notOr(int)
	 * @see #xOr(int)
	 * @see #same(int)
	 * @see #notSame(int)
	 */
	public static boolean xNotOr(int number) {
		return !xOr(number);
	}

	/**
	 * Used for determining if every <i>bit</i> is equal to every other <i>bit</i>.
	 * 
	 * @param number
	 * @return If every <i>bit</i> is equal to every other <i>bit</i>
	 * @see #and(int)
	 * @see #notAnd(int)
	 * @see #or(int)
	 * @see #notOr(int)
	 * @see #xOr(int)
	 * @see #xNotOr(int)
	 * @see #notSame(int)
	 */
	public static boolean same(int number) {
		if (number == 0b11111111_11111111_11111111_11111111) {
			return true;
		}
		return number == 0b00000000_00000000_00000000_00000000;
	}

	/**
	 * Used for determining if every <i>bit</i> is not equal to every other
	 * <i>bit</i>.
	 * 
	 * @param number
	 * @return If every <i>bit</i> is not equal to every other <i>bit</i>
	 * @see #and(int)
	 * @see #notAnd(int)
	 * @see #or(int)
	 * @see #notOr(int)
	 * @see #xOr(int)
	 * @see #xNotOr(int)
	 * @see #same(int)
	 */
	public static boolean notEqual(int number) {
		return !same(number);
	}

	/**
	 * Used for determining if every <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If every <i>bit</i> is set
	 * @see #notAnd(short)
	 * @see #or(short)
	 * @see #notOr(short)
	 * @see #xOr(short)
	 * @see #xNotOr(short)
	 * @see #same(short)
	 * @see #notSame(short)
	 */
	public static boolean and(short number) {
		return number == 0b11111111_11111111;
	}

	/**
	 * Used for determining if not every <i>bit</i> set.
	 * 
	 * @param number
	 * @return If not every <i>bit</i> is set
	 * @see #and(short)
	 * @see #or(short)
	 * @see #notOr(short)
	 * @see #xOr(short)
	 * @see #xNotOr(short)
	 * @see #same(short)
	 * @see #notSame(short)
	 */
	public static boolean notAnd(short number) {
		return !and(number);
	}

	/**
	 * Used for determining if at least one <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If at least one <i>bit</i> is set
	 * @see #and(short)
	 * @see #notAnd(short)
	 * @see #notOr(short)
	 * @see #xOr(short)
	 * @see #xNotOr(short)
	 * @see #same(short)
	 * @see #notSame(short)
	 */
	public static boolean or(short number) {
		return number != 0b00000000_00000000;
	}

	/**
	 * Used for determining if no <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If no <i>bit</i> is set
	 * @see #and(short)
	 * @see #notAnd(short)
	 * @see #or(short)
	 * @see #xOr(short)
	 * @see #xNotOr(short)
	 * @see #same(short)
	 * @see #notSame(short)
	 */
	public static boolean notOr(short number) {
		return !or(number);
	}

	/**
	 * Used for determining if exactly one <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If exactly one <i>bit</i> is set
	 * @see #and(short)
	 * @see #notAnd(short)
	 * @see #or(short)
	 * @see #notOr(short)
	 * @see #xNotOr(short)
	 * @see #same(short)
	 * @see #notSame(short)
	 */
	public static boolean xOr(short number) {
		return number != 0 && (number & (number - 1)) == 0;
	}

	/**
	 * Used for determining if not just one <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If not just one <i>bit</i> is set
	 * @see #and(short)
	 * @see #notAnd(short)
	 * @see #or(short)
	 * @see #notOr(short)
	 * @see #xOr(short)
	 * @see #same(short)
	 * @see #notSame(short)
	 */
	public static boolean xNotOr(short number) {
		return !xOr(number);
	}

	/**
	 * Used for determining if every <i>bit</i> is equal to every other <i>bit</i>.
	 * 
	 * @param number
	 * @return If every <i>bit</i> is equal to every other <i>bit</i>
	 * @see #and(short)
	 * @see #notAnd(short)
	 * @see #or(short)
	 * @see #notOr(short)
	 * @see #xOr(short)
	 * @see #xNotOr(short)
	 * @see #notSame(short)
	 */
	public static boolean same(short number) {
		if (number == 0b11111111_11111111) {
			return true;
		}
		return number == 0b00000000_00000000;
	}

	/**
	 * Used for determining if every <i>bit</i> is not equal to every other
	 * <i>bit</i>.
	 * 
	 * @param number
	 * @return If every <i>bit</i> is not equal to every other <i>bit</i>
	 * @see #and(short)
	 * @see #notAnd(short)
	 * @see #or(short)
	 * @see #notOr(short)
	 * @see #xOr(short)
	 * @see #xNotOr(short)
	 * @see #same(short)
	 */
	public static boolean notSame(short number) {
		return !same(number);
	}

	/**
	 * Used for determining if every <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If every <i>bit</i> is set
	 * @see #notAnd(byte)
	 * @see #or(byte)
	 * @see #notOr(byte)
	 * @see #xOr(byte)
	 * @see #xNotOr(byte)
	 * @see #same(byte)
	 * @see #notSame(byte)
	 */
	public static boolean and(byte number) {
		return number == 0b11111111;
	}

	/**
	 * Used for determining if not every <i>bit</i> set.
	 * 
	 * @param number
	 * @return If not every <i>bit</i> is set
	 * @see #and(byte)
	 * @see #or(byte)
	 * @see #notOr(byte)
	 * @see #xOr(byte)
	 * @see #xNotOr(byte)
	 * @see #same(byte)
	 * @see #notSame(byte)
	 */
	public static boolean notAnd(byte number) {
		return !and(number);
	}

	/**
	 * Used for determining if at least one <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If at least one <i>bit</i> is set
	 * @see #and(byte)
	 * @see #notAnd(byte)
	 * @see #notOr(byte)
	 * @see #xOr(byte)
	 * @see #xNotOr(byte)
	 * @see #same(byte)
	 * @see #notSame(byte)
	 */
	public static boolean or(byte number) {
		return number != 0b00000000;
	}

	/**
	 * Used for determining if no <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If no <i>bit</i> is set
	 * @see #and(byte)
	 * @see #notAnd(byte)
	 * @see #or(byte)
	 * @see #xOr(byte)
	 * @see #xNotOr(byte)
	 * @see #same(byte)
	 * @see #notSame(byte)
	 */
	public static boolean notOr(byte number) {
		return !or(number);
	}

	/**
	 * Used for determining if exactly one <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If exactly one <i>bit</i> is set
	 * @see #and(byte)
	 * @see #notAnd(byte)
	 * @see #or(byte)
	 * @see #notOr(byte)
	 * @see #xNotOr(byte)
	 * @see #same(byte)
	 * @see #notSame(byte)
	 */
	public static boolean xOr(byte number) {
		return number != 0 && (number & (number - 1)) == 0;
	}

	/**
	 * Used for determining if not just one <i>bit</i> is set.
	 * 
	 * @param number
	 * @return If not just one <i>bit</i> is set
	 * @see #and(byte)
	 * @see #notAnd(byte)
	 * @see #or(byte)
	 * @see #notOr(byte)
	 * @see #xOr(byte)
	 * @see #same(byte)
	 * @see #notSame(byte)
	 */
	public static boolean xNotOr(byte number) {
		return !xOr(number);
	}

	/**
	 * Used for determining if every <i>bit</i> is equal to every other <i>bit</i>.
	 * 
	 * @param number
	 * @return If every <i>bit</i> is equal to every other <i>bit</i>
	 * @see #and(byte)
	 * @see #notAnd(byte)
	 * @see #or(byte)
	 * @see #notOr(byte)
	 * @see #xOr(byte)
	 * @see #xNotOr(byte)
	 * @see #notSame(byte)
	 */
	public static boolean same(byte number) {
		if (number == 0b11111111) {
			return true;
		}
		return number == 0b00000000;
	}

	/**
	 * Used for determining if every <i>bit</i> is not equal to every other
	 * <i>bit</i>.
	 * 
	 * @param number
	 * @return If every <i>bit</i> is not equal to every other <i>bit</i>
	 * @see #and(byte)
	 * @see #notAnd(byte)
	 * @see #or(byte)
	 * @see #notOr(byte)
	 * @see #xOr(byte)
	 * @see #xNotOr(byte)
	 * @see #same(byte)
	 */
	public static boolean notSame(byte number) {
		return !same(number);
	}
}