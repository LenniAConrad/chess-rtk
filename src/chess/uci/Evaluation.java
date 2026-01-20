package chess.uci;

import java.util.Objects;

/**
 * The {@code Evaluation} class, also known as centipawn evaluation, represents
 * the standard UCI chess engine evaluation. The class contains three values, a
 * {@code Integer}, representing the centipawn evaluation. A {@code Boolean}
 * representing if it is mate. If it is mate, the {@code Integer} no longer
 * represents the centipawn evaluation, rather mate in {@code Integer} moves. As
 * well as another {@code boolean} indicating, whether the {@code Evaluation} is
 * valid and can be used.
 * 
 * @since 2023
 * @author Lennart A. Conrad
 */
public class Evaluation implements Comparable<Evaluation> {
	
	/**
	 * Regex used for identifying if a {@code String} matches the centipawn layout.
	 */
	private static final String CENTIPAWNREGEX = "-?\\d+";
	
	/**
	 * Regex used for identifying if a {@code String} matches the mate layout.
	 */
	private static final String MATEREGEX = "#-?\\d+";
	
	/**
	 * Used for indicating if the current {@code Evaluation} is valid and can be
	 * used for comparison.
	 */
	private boolean valid = false;

	/**
	 * Used for indicating if the current {@code Evaluation} is a mate. If it is
	 * mate, the value will represent a mate in ..., instead of the centipawn value.
	 */
	private boolean mate = false;

	/**
	 * Used for indicating the centipawn value of the current {@code Evaluation}.
	 */
	private int value = 0;

	/**
	 * Used for figuring out how different two {@code Evaluations} are from one and
	 * another.
	 * <ul>
	 * <li>{@code Discrepancy.TOTAL} means, that both {@code Evaluations} have
	 * mate.</li>
	 * <li>{@code Discrepancy.ABSOLUTE} means, that one {@code Evaluation} contains
	 * a mate.</li>
	 * <li>{@code Discrepancy.NORMAL} means, that no {@code Evaluation} contains a
	 * mate.</li>
	 * </ul>
	 */
	public enum Discrepancy {

		/**
		 * If two {@code Evaluations} are not mate.
		 */
		NORMAL,

		/**
		 * If one out of two {@code Evaluations} is a mate.
		 */
		ABSOLUTE,

		/**
		 * If two out of two {@code Evaluations} are mate.
		 */
		TOTAL
	}

	/**
	 * Used for creating a {@code Evaluation} from a mate {@code Boolean} and a
	 * {@code Integer} value.
	 * 
	 * @param mate
	 * @param value
	 */
	public Evaluation(boolean mate, int value) {
		this.valid = true;
		this.mate = mate;
		this.value = value;
	}

	/**
	 * Used for creating a {@code Evaluation} from a {@code String}.
	 *
	 * @param evaluation the evaluation string to parse
	 * @throws IllegalArgumentException if the evaluation string is invalid or cannot be parsed
	 */
	public Evaluation(String evaluation) {
		try {
			if (evaluation.matches(CENTIPAWNREGEX)) {
				value = Integer.parseInt(evaluation);
			} else if (evaluation.matches(MATEREGEX)) {
				value = Integer.parseInt(evaluation.substring(1));
				mate = true;
			} else {
				throw new NumberFormatException();
			}
			valid = true;
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Invalid evaluation format: " + evaluation, ex);
		}
	}

	/**
	 * Used for inverting the current {@code Evaluation}.
	 * 
	 * <p>
	 * Inverting a {@code Evaluation} can be useful if you want to skip evaluating
	 * certain positions. The following {@code Position}
	 * '8/p1p5/1p4r1/8/6p1/P7/1P1Q1k2/K7 b - - 0 11' is evaluated with:
	 * </p>
	 * 
	 * <pre> info depth 32 seldepth 69 multipv 1 score cp 544 wdl 1000 0 0
	 * nodes 17065467 nps 1333656 hashfull 998 tbhits 0 time 12796 pv g7h8 f4f3 h2h5
	 * g5f4 h8d4 f5e4 d4d2 e4e3 d2h2 f4e4 h2e5 e4d3 e5c3 d3e2 c3c2 e3d2 h5e5 e2f1
	 * c2d2 f3f2 d2d3 f1g2 d3e4 g2g1 e4g6 f2f1q a1a2 f1c4 b2b3 c4f4 g6b1 g1f2 b1c2
	 * f2g3 e5e2 c7c5 c2d3 g3h4 d3h7 h4g5 h7a7 f4d6 a7g7 g5h4 g7h8 h4g5 h8g8 g5h4
	 * g8c4 h4g5 c4c1 g5g6 c1e3 d6d4 </pre>
	 * 
	 * <p>
	 * A chess {@code Engine}, that is perfect would only ever evaluate a position
	 * with <i>Mate for White</i>, <i>Draw</i> or <i>Mate for Black</i>. Chess
	 * {@code Engines}, however, are not perfect. If we now play the move Qh8, we
	 * reach the {@code Position} ' 7Q/p1p5/1p4r1/5qk1/5pp1/P7/1P5R/K7 b - - 1 1'
	 * and can conclude that our synthesized {@code Evaluation} should look like
	 * this:
	 * </p>
	 * 
	 * <pre> info depth 31 seldepth 53 multipv 1 score cp -544 wdl 0 0 1000
	 * nodes ??? nps 1333656 hashfull 998 tbhits 0 time 12796 pv f4f3 h2h5 g5f4 h8d4
	 * f5e4 d4d2 e4e3 d2h2 f4e4 h2e5 e4d3 e5c3 d3e2 c3c2 e3d2 h5e5 e2f1 c2d2 f3f2
	 * d2d3 f1g2 d3e4 g2g1 e4g6 f2f1q a1a2 f1c4 b2b3 c4f4 g6b1 g1f2 b1c2 f2g3 e5e2
	 * c7c5 c2d3 g3h4 d3h7 h4g5 h7a7 f4d6 a7g7 g5h4 g7h8 h4g5 h8g8 g5h4 g8c4 h4g5
	 * c4c1 g5g6 c1e3 d6d4 </pre>
	 * 
	 * <p>
	 * The {@code Evaluation} has just switched, a {@code Position} that is good for
	 * White must be bad for Black. The {@code Evaluation} however, is not perfect.
	 * It is just a conclusion made from the {@code Engine} {@code Output}.
	 * </p>
	 * 
	 * <p>
	 * Inversion examples:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th> Original </th> <th> Inverted </th> </tr>
	 * 		<tr> <td> M1 </td> <td> M-1 </td> </tr>
	 *   	<tr> <td> M10 </td> <td> M-10 </td> </tr>
	 *    	<tr> <td> 1000 </td> <td> -1000 </td> </tr>
	 *     	<tr> <td> 100 </td> <td> -100 </td> </tr>
	 *      <tr> <td> 0 </td> <td> 0 </td> </tr>
	 *      <tr> <td> -100 </td> <td> 100 </td> </tr>
	 *      <tr> <td> -1000 </td> <td> 1000 </td> </tr>
	 *      <tr> <td> M-10 </td> <td> M10 </td> </tr>
	 *      <tr> <td> M-1 </td> <td> M1 </td> </tr>   
	 * 	</table>
	 * </pre>
	 */
	public Evaluation invert() {
		value = - value;
		return this;
	}

	/**
	 * Used for retrieving the validity {@code boolean} of the current
	 * {@code Evaluation}.
	 * 
	 * @return The validity {@code boolean} of the current {@code Evaluation}
	 */
	public boolean isValid() {
		return valid;
	}

	/**
	 * Used for retrieving the mate {@code boolean} of the current
	 * {@code Evaluation}.
	 * 
	 * @return The mate {@code boolean} of the current {@code Evaluation}
	 */
	public boolean isMate() {
		return mate;
	}

	/**
	 * Used for retrieving the value of the current {@code Evaluation}.
	 * 
	 * @return The value of the current {@code Evaluation}
	 */
	public int getValue() {
		return value;
	}

	/**
	 * Used for comparing {@code Evaluations} to one and another.
	 * 
	 * <ul>
	 * <li>If one has mate and the other one does not, it will return
	 * {@code Discrepancy.Absolute}.</li>
	 * <li>If both have mate, it will return {@code Discrepancy.Total}.</li>
	 * <li>If none apply, it will return {@code Discrepancy.Normal}.</li>
	 * </ul>
	 * This is especially useful when trying to figure out, if two evaluations are
	 * not too similar.
	 * 
	 * @param evaluation
	 * @return The {@code Discrepancy} of the two {@code Evaluations}
	 */
	public Discrepancy getDiscrepancy(Evaluation evaluation) {
		if (mate) {
			if (evaluation.mate) {
				return Discrepancy.TOTAL;
			}
			return Discrepancy.ABSOLUTE;
		}
		if (evaluation.mate) {
			return Discrepancy.ABSOLUTE;
		}
		return Discrepancy.NORMAL;
	}

	/**
	 * Used for converting the current {@code Evaluation} to a {@code String}.
	 * 
	 * @return The current {@code Evaluation} as a human readable {@code String}
	 */
	@Override
	public String toString() {
		if (mate) {
			return "#" + value;
		}
		return Double.toString(value / 100.0);
	}

    /**
     * Used for checking if an evaluation is more beneficial than another. An evaluation is more
     * beneficial when:
     *
     * <ul>
     * <li>It is closer to checkmating the opponent.</li>
     * <li>It is further from being checkmated.</li>
     * <li>It has checkmate and the other doesn't.</li>
     * <li>It is not being checkmated and the other is.</li>
     * <li>It has a greater centipawn value.</li>
     * </ul>
     *
     * Example: {@code M1 > M10 > 1000 > 100 > 0 > -100 > -1000 > M-10 > M-1}
     *
     * @param evaluation the other {@code Evaluation} to compare with
     * @return true if this evaluation is more beneficial than the other
     */
    public boolean isGreater(Evaluation evaluation) {
        if (!valid || !evaluation.valid) {
            return false;
        }
        return compareMate(evaluation) > 0;
    }

    /**
     * Used for checking if an evaluation is more or as beneficial as another. A
     * evaluation is more or as beneficial, when:
     *
     * <ul>
     * <li>One side is closer to checkmating the opponent.</li>
     * <li>One side is further away from being checkmated by the opponent.</li>
     * <li>One side has checkmate and the other one doesn't.</li>
     * <li>One side is not being checkmated and the other one is.</li>
     * <li>One side has a greater centipawn value.</li>
     * </ul>
     *
     * Example:
     *
     * <pre> M1 > M10 > 1000 > 100 > 0 > -100 > -1000 > M-10 > M-1
     * </pre>
     *
     * @param evaluation the other {@code Evaluation} to compare with
     * @return true if this evaluation has a greater or equal value than the other
     */
    public boolean isGreaterEqual(Evaluation evaluation) {
        if (!valid || !evaluation.valid) {
            return false;
        }
        return compareMate(evaluation) >= 0;
    }

    /**
     * Used for checking if an evaluation is as beneficial as another.
     *
     * Example:
     *
     * <pre> M1 > M10 > 1000 > 100 > 0 > -100 > -1000 > M-10 > M-1
     * </pre>
     *
     * @param evaluation the other {@code Evaluation} to compare with
     * @return true if both evaluations are valid, have the same mate status, and equal value
     */
    public boolean isEqual(Evaluation evaluation) {
        if (!valid || !evaluation.valid) {
            return false;
        }
        return this.mate == evaluation.mate && this.value == evaluation.value;
    }

    /**
     * Used for checking if an evaluation is less beneficial than another. A
     * evaluation is less beneficial, when:
     *
     * <ul>
     * <li>One side is further away from checkmating the opponent.</li>
     * <li>One side is closer to being checkmated by the opponent.</li>
     * <li>One side doesn't have checkmate and the other one has.</li>
     * <li>One side is being checkmated and the other one is not.</li>
     * <li>One side has a lesser centipawn value.</li>
     * </ul>
     *
     * Example:
     *
     * <pre> M1 > M10 > 1000 > 100 > 0 > -100 > -1000 > M-10 > M-1
     * </pre>
     *
     * @param evaluation the other {@code Evaluation} to compare with
     * @return true if this evaluation is less beneficial than the other
     */
    public boolean isLess(Evaluation evaluation) {
        if (!valid || !evaluation.valid) {
            return false;
        }
        return compareMate(evaluation) < 0;
    }

    /**
     * Used for checking if an evaluation is less or as beneficial as another. A
     * evaluation is less or as beneficial, when:
     *
     * <ul>
     * <li>One side is further away from checkmating the opponent.</li>
     * <li>One side is closer to being checkmated by the opponent.</li>
     * <li>One side doesn't have checkmate and the other one has.</li>
     * <li>One side is being checkmated and the other one is not.</li>
     * <li>One side has a lesser centipawn value.</li>
     * </ul>
     *
     * Example:
     *
     * <pre> M1 > M10 > 1000 > 100 > 0 > -100 > -1000 > M-10 > M-1
     * </pre>
     *
     * @param evaluation the other {@code Evaluation} to compare with
     * @return true if this evaluation has a lesser or equal value than the other
     */
    public boolean isLessEqual(Evaluation evaluation) {
        if (!valid || !evaluation.valid) {
            return false;
        }
        return compareMate(evaluation) <= 0;
    }

    /**
     * Core mate-based comparison: returns >0 if this is more beneficial,
     * <0 if less, or 0 if equal.
     */
    private int compareMate(Evaluation other) {
        if (this.mate && other.mate) {
            return other.value - this.value;
        }
        if (this.mate) {
			return 1;
		}
		if (other.mate) { 
			return -1;
		}
        return this.value - other.value;
    }

    /**
     * Used for comparing two {@code Evaluations} with one and another. Typically
     * used in methods, such as {@code Arrays.sort()}.
     */
    @Override
    public int compareTo(Evaluation evaluation) {
        if (!valid || !evaluation.valid) {
            return 0;
        }
        return -compareMate(evaluation);
    }

	/**
	 * Used for checking equality consistent with {@code compareTo}: two valid evaluations are equal if
	 * they share mate status and value.
	 *
	 * @param obj the object to compare with
	 * @return true if the given object is equal to this evaluation
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Evaluation)) {
			return false;
		}
		Evaluation other = (Evaluation) obj;
		if (!valid || !other.valid) {
			return false;
		}
		return this.mate == other.mate && this.value == other.value;
	}

	/**
	 * Used for generating a hash code consistent with equals, including validity, mate status, and
	 * value.
	 *
	 * @return hash code value for the object
	 */
	@Override
	public int hashCode() {
		return Objects.hash(valid, mate, value);
	}

	/**
	 * Used for creating a deep-copy of the current {@code Evaluation}.
	 *
	 * @param other the {@code Evaluation} object to copy
	 */
	public Evaluation(Evaluation other) {
		this.valid = other.valid;
		this.mate = other.mate;
		this.value = other.value;
	}
	
}
