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
	 * Used for creating a {@code Evaluation} from a mate {@code Boolean} and a
	 * {@code Integer} value.
	 *
	 * @param mate mate flag
	 * @param value value to use
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
        if (!valid || evaluation == null || !evaluation.valid) {
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
        if (!valid || evaluation == null || !evaluation.valid) {
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
        if (!valid || evaluation == null || !evaluation.valid) {
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
        if (!valid || evaluation == null || !evaluation.valid) {
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
        if (!valid || evaluation == null || !evaluation.valid) {
            return false;
        }
        return compareMate(evaluation) <= 0;
    }

    /**
     * Core mate-based comparison: returns >0 if this is more beneficial,
     * <0 if less, or 0 if equal.
     * @param other source other
     * @return compare mate
     */
    private int compareMate(Evaluation other) {
        if (this.mate && other.mate) {
            if (this.value > 0 && other.value < 0) {
                return 1;
            }
            if (this.value < 0 && other.value > 0) {
                return -1;
            }
            return other.value - this.value;
        }
        if (this.mate) {
            return this.value > 0 ? 1 : -1;
        }
        if (other.mate) {
            return other.value > 0 ? -1 : 1;
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
