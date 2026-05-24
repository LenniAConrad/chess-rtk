package utility;

/**
 * Small numeric helpers shared by rendering, training export, and evaluation
 * code.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Numbers {

	/**
	 * Utility class; prevent instantiation.
	 */
	private Numbers() {
		// utility
	}

	/**
	 * Clamps an integer to the inclusive range {@code [min, max]}.
	 *
	 * @param value value to clamp
	 * @param min lower bound
	 * @param max upper bound
	 * @return clamped value
	 */
	public static int clamp(int value, int min, int max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	/**
	 * Clamps a float to the inclusive range {@code [min, max]}.
	 *
	 * @param value value to clamp
	 * @param min lower bound
	 * @param max upper bound
	 * @return clamped value
	 */
	public static float clamp(float value, float min, float max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	/**
	 * Clamps a double to the inclusive range {@code [min, max]}.
	 *
	 * @param value value to clamp
	 * @param min lower bound
	 * @param max upper bound
	 * @return clamped value
	 */
	public static double clamp(double value, double min, double max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	/**
	 * Clamps a double into the unit interval.
	 *
	 * @param value source value
	 * @return clamped value in {@code [0, 1]}
	 */
	public static double clamp01(double value) {
		return clamp(value, 0.0, 1.0);
	}

	/**
	 * Clamps and rounds a floating-point color channel into an 8-bit channel.
	 *
	 * @param value source channel
	 * @return integer channel in {@code [0, 255]}
	 */
	public static int clampByte(double value) {
		if (value <= 0.0) {
			return 0;
		}
		if (value >= 255.0) {
			return 255;
		}
		return (int) Math.round(value);
	}
}
